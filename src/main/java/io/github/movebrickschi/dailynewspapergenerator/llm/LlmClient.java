package io.github.movebrickschi.dailynewspapergenerator.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 通用大模型 HTTP 客户端，基于 OpenAI 兼容 Chat Completions 协议。
 * 适用于智谱 GLM / DeepSeek / 通义千问 / Kimi / OpenAI / Ollama 等任意兼容厂商。
 * <p>
 * 支持两种调用方式：
 * <ul>
 *   <li>{@link #chat(String, String)}：阻塞式，一次性返回完整文本</li>
 *   <li>{@link #chatStream(String, String, Consumer, Supplier)}：流式回调，可中断</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final Gson GSON = new Gson();

    private final LlmSettings settings;
    private final HttpClient httpClient;

    public LlmClient(LlmSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 非流式调用。内部仍然以流式方式发起请求，方便统一处理；对外按完整文本返回。
     */
    public String chat(String systemPrompt, String userContent) {
        StringBuilder full = new StringBuilder();
        chatStream(systemPrompt, userContent, full::append, () -> false);
        return full.toString();
    }

    /**
     * 流式调用：每接收到一段 delta 就回调 {@code onDelta}；当 {@code isCancelled} 返回 true 时尽快中断。
     *
     * @param systemPrompt 系统提示词，可为空
     * @param userContent  用户消息，可为空
     * @param onDelta      增量回调（可为空）
     * @param isCancelled  取消信号（可为空，等价于永不取消）
     */
    public void chatStream(String systemPrompt,
                           String userContent,
                           Consumer<String> onDelta,
                           Supplier<Boolean> isCancelled) {
        String endpoint = buildEndpoint(settings.baseUrl);
        String payload = buildPayload(systemPrompt, userContent, true);

        int timeout = settings.timeoutSeconds > 0 ? settings.timeoutSeconds : 60;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + nullSafe(settings.apiKey))
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        log.info("向大模型发起流式请求: endpoint={}, model={}", endpoint, settings.model);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new LlmException("调用大模型失败: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = readAll(response.body());
            log.error("大模型返回非成功状态 status={}, body={}", status, truncate(body, 500));
            throw new LlmException("大模型返回错误(HTTP " + status + "): " + truncate(body, 500));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled != null && Boolean.TRUE.equals(isCancelled.get())) {
                    log.info("流式请求被取消");
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                try {
                    JsonObject obj = JsonParser.parseString(data).getAsJsonObject();
                    JsonArray choices = obj.getAsJsonArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        continue;
                    }
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    String delta = extractDelta(choice);
                    if (delta != null && !delta.isEmpty() && onDelta != null) {
                        onDelta.accept(delta);
                    }
                } catch (Exception parseEx) {
                    log.warn("解析 SSE 行失败 line={}, err={}", truncate(data, 200), parseEx.getMessage());
                }
            }
        } catch (Exception e) {
            throw new LlmException("读取流式响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简单连通性测试：发起一次最短 prompt 的非流式请求，捕获状态码与 body 用于反馈。
     *
     * @return 200 范围返回 success；其它返回带 HTTP code 与简短 body
     */
    public PingResult ping() {
        String endpoint = buildEndpoint(settings.baseUrl);
        String payload = buildPayload("", "ping", false);
        int timeout = Math.min(settings.timeoutSeconds > 0 ? settings.timeoutSeconds : 60, 15);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + nullSafe(settings.apiKey))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                return new PingResult(true, status, "OK");
            }
            return new PingResult(false, status, truncate(resp.body(), 300));
        } catch (java.net.http.HttpTimeoutException e) {
            return new PingResult(false, -1, "请求超时: " + e.getMessage());
        } catch (Exception e) {
            return new PingResult(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public record PingResult(boolean ok, int status, String message) {
    }

    private static String extractDelta(JsonObject choice) {
        if (choice.has("delta") && choice.get("delta").isJsonObject()) {
            JsonObject delta = choice.getAsJsonObject("delta");
            if (delta.has("content") && !delta.get("content").isJsonNull()
                    && delta.get("content").isJsonPrimitive()) {
                return delta.get("content").getAsString();
            }
        }
        if (choice.has("message") && choice.get("message").isJsonObject()) {
            JsonObject msg = choice.getAsJsonObject("message");
            if (msg.has("content") && !msg.get("content").isJsonNull()
                    && msg.get("content").isJsonPrimitive()) {
                return msg.get("content").getAsString();
            }
        }
        return null;
    }

    private static String readAll(InputStream is) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String buildEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmException("Base URL 未配置");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        return trimmed + "/chat/completions";
    }

    private String buildPayload(String systemPrompt, String userContent, boolean stream) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        Map<String, String> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userContent == null ? "" : userContent);
        messages.add(user);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model);
        body.put("messages", messages);
        body.put("stream", stream);

        return GSON.toJson(body);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
