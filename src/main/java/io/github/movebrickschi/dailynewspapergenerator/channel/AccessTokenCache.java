package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * 钉钉 OAPI access_token 进程内缓存。
 * <p>
 * 调用 {@code https://oapi.dingtalk.com/gettoken?appkey=...&appsecret=...} 获取 token，
 * 默认 7100 秒过期（官方 7200，留 100 秒缓冲）。
 * <p>
 * <b>缓存 key 改进</b>：使用 {@code sha256(appKey|appSecret)} 而非明文拼接，避免
 * appSecret 落入内存中 Map 的 key 字符串。<br>
 * <b>容量上限</b>：内部使用带最大条目数（{@link #MAX_ENTRIES}）的 LRU，
 * 防止用户多次切换不同 AppKey/secret 后旧条目永驻内存。
 *
 * @author Liu Chunchi
 */
public final class AccessTokenCache {

    /** 缓存最大条目数（LRU 淘汰）。 */
    private static final int MAX_ENTRIES = 32;

    private static final Map<String, Entry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Entry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /** 同 AppKey 同时取 token 的 single-flight 锁。 */
    private static final ConcurrentHashMap<String, CompletableFuture<String>> INFLIGHT = new ConcurrentHashMap<>();
    private static final long EXPIRES_MS = 7100L * 1000;

    private AccessTokenCache() {
    }

    public static String get(String appKey, String appSecret) throws Exception {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("AppKey/AppSecret 未配置");
        }
        String cacheKey = cacheKey(appKey, appSecret);
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(cacheKey);
        if (e != null && now < e.expireAt) {
            return e.token;
        }

        CompletableFuture<String> inflight = INFLIGHT.computeIfAbsent(cacheKey, k -> {
            CompletableFuture<String> f = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                try {
                    String token = fetch(appKey, appSecret);
                    CACHE.put(cacheKey, new Entry(token, System.currentTimeMillis() + EXPIRES_MS));
                    f.complete(token);
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                } finally {
                    INFLIGHT.remove(cacheKey);
                }
            });
            return f;
        });
        try {
            return inflight.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause == null ? ee : cause);
        }
    }

    /** 钉钉返回 errcode 40014/42001 时调用，强制下次重取。 */
    public static void invalidate(String appKey, String appSecret) {
        if (appKey == null || appSecret == null) {
            return;
        }
        CACHE.remove(cacheKey(appKey, appSecret));
    }

    private static String fetch(String appKey, String appSecret) throws Exception {
        String url = "https://oapi.dingtalk.com/gettoken?appkey="
                + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                + "&appsecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
        HttpResponse<String> resp = HttpUtil.get(url);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("gettoken HTTP " + resp.statusCode() + ": " + SenderSupport.truncate(resp.body()));
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        int code = obj.has("errcode") ? obj.get("errcode").getAsInt() : -1;
        if (code != 0 || !obj.has("access_token")) {
            String msg = obj.has("errmsg") ? obj.get("errmsg").getAsString() : SenderSupport.truncate(resp.body());
            throw new RuntimeException("gettoken errcode=" + code + ", errmsg=" + msg);
        }
        return obj.get("access_token").getAsString();
    }

    /** sha256(appKey + "\u001f" + appSecret) → hex；避免明文 secret 残留在 Map 内部。 */
    private static String cacheKey(String appKey, String appSecret) {
        String joined = appKey + "\u001f" + appSecret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // 任意 JRE 必带 SHA-256；走到这里只能回退到明文哈希，依然不暴露 secret 给 Map key 之外。
            return Integer.toHexString(joined.hashCode());
        }
    }

    /** 仅用于调试 / 测试：返回当前缓存内容副本。 */
    public static Map<String, Entry> snapshot() {
        synchronized (CACHE) {
            return Map.copyOf(CACHE);
        }
    }

    public record Entry(String token, long expireAt) {
    }
}
