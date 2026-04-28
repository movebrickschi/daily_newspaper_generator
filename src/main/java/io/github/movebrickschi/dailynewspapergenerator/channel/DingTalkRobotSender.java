package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉自定义机器人 webhook 发送方。使用 markdown 消息。
 * 文档: https://open.dingtalk.com/document/orgapp/custom-robots-send-group-messages
 *
 * @author Liu Chunchi
 */
public class DingTalkRobotSender implements ChannelSender {

    private static final Gson GSON = new Gson();

    @Override
    public ChannelConfig.ChannelType supportedType() {
        return ChannelConfig.ChannelType.DINGTALK_ROBOT;
    }

    @Override
    public @NotNull SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.webhookUrl == null || config.webhookUrl.isBlank()) {
            return SendResult.failure("Webhook URL 未配置");
        }
        String url = config.webhookUrl;
        String secret = SecureKeyStore.load(config.secretKey());
        if (secret != null && !secret.isBlank()) {
            try {
                long ts = System.currentTimeMillis();
                String sign = sign(ts, secret);
                String join = url.contains("?") ? "&" : "?";
                url = url + join + "timestamp=" + ts + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return SendResult.failure("加签失败: " + e.getMessage());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", emptyToDefault(title, config.title, "日报"));
        markdown.put("text", content == null ? "" : content);
        body.put("markdown", markdown);

        try {
            HttpResponse<String> resp = HttpUtil.postJson(url, GSON.toJson(body));
            return parse(resp);
        } catch (Exception e) {
            return SendResult.failure("HTTP 请求失败: " + e.getMessage());
        }
    }

    private SendResult parse(HttpResponse<String> resp) {
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return SendResult.failure("HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        try {
            JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
            int code = obj.has("errcode") ? obj.get("errcode").getAsInt() : -1;
            if (code == 0) {
                return SendResult.ok();
            }
            String msg = obj.has("errmsg") ? obj.get("errmsg").getAsString() : truncate(resp.body());
            return SendResult.failure("钉钉返回 errcode=" + code + ", errmsg=" + msg);
        } catch (Exception e) {
            return SendResult.failure("解析钉钉响应失败: " + e.getMessage());
        }
    }

    static String sign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return new String(Base64.getEncoder().encode(signed), StandardCharsets.UTF_8);
    }

    private static String emptyToDefault(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "";
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
