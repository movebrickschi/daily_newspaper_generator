package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉 OAPI access_token 进程内缓存。
 * <p>
 * 调用 {@code https://oapi.dingtalk.com/gettoken?appkey=...&appsecret=...} 获取 token，
 * 默认 7100 秒过期（官方 7200，留 100 秒缓冲）。
 *
 * @author Liu Chunchi
 */
public final class AccessTokenCache {

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long EXPIRES_MS = 7100L * 1000;

    private AccessTokenCache() {
    }

    public static String get(String appKey, String appSecret) throws Exception {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("AppKey/AppSecret 未配置");
        }
        String cacheKey = appKey + "|" + appSecret;
        Entry e = CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (e != null && now < e.expireAt) {
            return e.token;
        }
        String token = fetch(appKey, appSecret);
        CACHE.put(cacheKey, new Entry(token, now + EXPIRES_MS));
        return token;
    }

    /** 钉钉返回 errcode 40014/42001 时调用，强制下次重取。 */
    public static void invalidate(String appKey, String appSecret) {
        CACHE.remove(appKey + "|" + appSecret);
    }

    private static String fetch(String appKey, String appSecret) throws Exception {
        String url = "https://oapi.dingtalk.com/gettoken?appkey="
                + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                + "&appsecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
        HttpResponse<String> resp = HttpUtil.get(url);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("gettoken HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        int code = obj.has("errcode") ? obj.get("errcode").getAsInt() : -1;
        if (code != 0 || !obj.has("access_token")) {
            String msg = obj.has("errmsg") ? obj.get("errmsg").getAsString() : truncate(resp.body());
            throw new RuntimeException("gettoken errcode=" + code + ", errmsg=" + msg);
        }
        return obj.get("access_token").getAsString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    /** 仅用于调试 / 测试：返回当前缓存内容副本。 */
    public static Map<String, Entry> snapshot() {
        return Map.copyOf(CACHE);
    }

    public record Entry(String token, long expireAt) {
    }
}
