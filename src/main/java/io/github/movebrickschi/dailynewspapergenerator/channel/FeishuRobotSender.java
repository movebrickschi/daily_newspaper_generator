package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书自定义机器人 webhook 发送方。使用 interactive 卡片消息。
 * 文档: https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/bot-v2/use-custom-bots-in-a-group
 *
 * @author Liu Chunchi
 */
public class FeishuRobotSender implements ChannelSender {

    private static final Gson GSON = new Gson();

    @Override
    public ChannelConfig.ChannelType supportedType() {
        return ChannelConfig.ChannelType.FEISHU_ROBOT;
    }

    @Override
    public @NotNull SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.webhookUrl == null || config.webhookUrl.isBlank()) {
            return SendResult.failure("Webhook URL 未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");

        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> headerTitle = new LinkedHashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", emptyToDefault(title, config.title, "日报"));
        header.put("title", headerTitle);
        header.put("template", "blue");
        card.put("header", header);

        Map<String, Object> markdownEl = new LinkedHashMap<>();
        markdownEl.put("tag", "markdown");
        markdownEl.put("content", content == null ? "" : content);

        card.put("elements", List.of(markdownEl));
        body.put("card", card);

        // 加签
        String secret = SecureKeyStore.load(config.secretKey());
        if (secret != null && !secret.isBlank()) {
            long ts = System.currentTimeMillis() / 1000;
            try {
                String sign = sign(ts, secret);
                body.put("timestamp", String.valueOf(ts));
                body.put("sign", sign);
            } catch (Exception e) {
                return SendResult.failure("加签失败: " + e.getMessage());
            }
        }

        try {
            HttpResponse<String> resp = HttpUtil.postJson(config.webhookUrl, GSON.toJson(body));
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
            int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
            if (code == 0) {
                return SendResult.ok();
            }
            String msg = obj.has("msg") ? obj.get("msg").getAsString() : truncate(resp.body());
            return SendResult.failure("飞书返回 code=" + code + ", msg=" + msg);
        } catch (Exception e) {
            return SendResult.failure("解析飞书响应失败: " + e.getMessage());
        }
    }

    static String sign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal(new byte[]{});
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
