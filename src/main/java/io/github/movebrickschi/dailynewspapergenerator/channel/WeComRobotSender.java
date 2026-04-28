package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人 webhook 发送方。使用 markdown 消息。
 * 文档: https://developer.work.weixin.qq.com/document/path/91770
 *
 * @author Liu Chunchi
 */
public class WeComRobotSender implements ChannelSender {

    private static final Gson GSON = new Gson();

    @Override
    public ChannelConfig.ChannelType supportedType() {
        return ChannelConfig.ChannelType.WECOM_ROBOT;
    }

    @Override
    public @NotNull SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.webhookUrl == null || config.webhookUrl.isBlank()) {
            return SendResult.failure("Webhook URL 未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> markdown = new LinkedHashMap<>();
        String md = content == null ? "" : content;
        // 企微 markdown 不支持很多 # 级别，简单加个标题
        if (title != null && !title.isBlank()) {
            md = "# " + title + "\n\n" + md;
        }
        markdown.put("content", md);
        body.put("markdown", markdown);
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
            int code = obj.has("errcode") ? obj.get("errcode").getAsInt() : -1;
            if (code == 0) {
                return SendResult.ok();
            }
            String msg = obj.has("errmsg") ? obj.get("errmsg").getAsString() : truncate(resp.body());
            return SendResult.failure("企微返回 errcode=" + code + ", errmsg=" + msg);
        } catch (Exception e) {
            return SendResult.failure("解析企微响应失败: " + e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
