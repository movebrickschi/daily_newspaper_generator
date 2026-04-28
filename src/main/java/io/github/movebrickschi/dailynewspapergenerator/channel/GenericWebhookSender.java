package io.github.movebrickschi.dailynewspapergenerator.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 webhook 发送方。
 * <p>
 * 请求体使用 {@link ChannelConfig#genericTemplate}，支持占位符 <code>{{title}}</code> / <code>{{content}}</code>，
 * 替换为转义后的 JSON 字符串值；若 secret 配置不为空，会以 <code>Authorization: Bearer &lt;secret&gt;</code> 的形式带上。
 *
 * @author Liu Chunchi
 */
public class GenericWebhookSender implements ChannelSender {

    @Override
    public ChannelConfig.ChannelType supportedType() {
        return ChannelConfig.ChannelType.GENERIC_WEBHOOK;
    }

    @Override
    public @NotNull SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.webhookUrl == null || config.webhookUrl.isBlank()) {
            return SendResult.failure("Webhook URL 未配置");
        }
        String tmpl = config.genericTemplate == null || config.genericTemplate.isBlank()
                ? "{\"title\":\"{{title}}\",\"content\":\"{{content}}\"}"
                : config.genericTemplate;

        String json = tmpl
                .replace("{{title}}", escapeJson(title == null ? "" : title))
                .replace("{{content}}", escapeJson(content == null ? "" : content));

        // 校验是否为合法 JSON
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject() && !el.isJsonArray()) {
                return SendResult.failure("请求体模板渲染后不是合法 JSON 对象/数组");
            }
        } catch (Exception e) {
            return SendResult.failure("请求体模板渲染后不是合法 JSON: " + e.getMessage());
        }

        Map<String, String> headers = new LinkedHashMap<>();
        String token = SecureKeyStore.load(config.secretKey());
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> resp = HttpUtil.postJson(config.webhookUrl, json, headers);
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                return SendResult.ok("HTTP " + status);
            }
            return SendResult.failure("HTTP " + status + ": " + truncate(resp.body()));
        } catch (Exception e) {
            return SendResult.failure("HTTP 请求失败: " + e.getMessage());
        }
    }

    /** 把字符串值转成可塞进 JSON 字符串字面量的形式（不带外层引号）。 */
    static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
