package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉「日志 / 智能汇报」模板发送方。
 * <p>
 * 调用 {@code POST /topapi/report/create}，把整份报告塞到 {@link ChannelConfig#dingMainField} 对应字段。
 * 文档: https://open.dingtalk.com/document/orgapp/create-a-log
 *
 * @author Liu Chunchi
 */
public class DingTalkReportSender implements ChannelSender {

    private static final Gson GSON = new Gson();

    @Override
    public ChannelConfig.ChannelType supportedType() {
        return ChannelConfig.ChannelType.DINGTALK_REPORT;
    }

    @Override
    public @NotNull SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.dingAppKey == null || config.dingAppKey.isBlank()) {
            return SendResult.failure("AppKey 未配置");
        }
        String secret = SecureKeyStore.load(config.secretKey());
        if (secret == null || secret.isBlank()) {
            return SendResult.failure("AppSecret 未配置");
        }
        if (config.dingTemplateId == null || config.dingTemplateId.isBlank()) {
            return SendResult.failure("Template ID 未配置");
        }
        if (config.dingMainField == null || config.dingMainField.isBlank()) {
            return SendResult.failure("主字段名未配置");
        }
        if (config.dingToUserIds == null || config.dingToUserIds.isBlank()) {
            return SendResult.failure("接收人 userid 未配置");
        }

        try {
            return doSend(config, secret, title, content, false);
        } catch (Exception e) {
            return SendResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private SendResult doSend(ChannelConfig config, String secret, String title, String content, boolean retried) throws Exception {
        String token = AccessTokenCache.get(config.dingAppKey, secret);

        // contents 数组里的第一个 userid 当 reporter
        String firstUserId = firstUserId(config.dingToUserIds);

        Map<String, Object> createReportRequest = new LinkedHashMap<>();
        createReportRequest.put("template_id", config.dingTemplateId);
        createReportRequest.put("userid", firstUserId);
        createReportRequest.put("dd_from", "DailyReportPlugin");
        createReportRequest.put("to_chat", config.dingToChat);
        createReportRequest.put("to_userids", config.dingToUserIds);

        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("sort", "0");
        contentItem.put("type", "0");
        contentItem.put("content_type", "markdown");
        contentItem.put("key", config.dingMainField);
        String body = (title == null || title.isBlank()) ? content : ("# " + title + "\n\n" + content);
        contentItem.put("value", body);
        createReportRequest.put("contents", List.of(contentItem));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("create_report_param", createReportRequest);

        String url = "https://oapi.dingtalk.com/topapi/report/create?access_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpResponse<String> resp = HttpUtil.postJson(url, GSON.toJson(root));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return SendResult.failure("HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        int code = obj.has("errcode") ? obj.get("errcode").getAsInt() : -1;
        if (code == 0) {
            return SendResult.ok();
        }
        String msg = obj.has("errmsg") ? obj.get("errmsg").getAsString() : truncate(resp.body());
        if ((code == 40014 || code == 42001) && !retried) {
            AccessTokenCache.invalidate(config.dingAppKey, secret);
            return doSend(config, secret, title, content, true);
        }
        return SendResult.failure("钉钉日志返回 errcode=" + code + ", errmsg=" + msg);
    }

    private static String firstUserId(String csv) {
        if (csv == null) return "";
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
