package io.github.movebrickschi.dailynewspapergenerator.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String USER_AGENT = "DailyReportPlugin/1.4.0 (IntelliJ Platform)";

    /**
     * 全局共享的 HttpClient 实例。HttpClient 内部维护连接池，
     * 共享可显著降低每次请求的 TCP/TLS 握手开销。
     * <p>
     * 注入独立 {@link CookieManager}（不接受任何 cookie）以避免跟随
     * JVM 默认 {@code CookieHandler}，杜绝在 IDE 进程中长期累积 cookie 状态
     * 被泄漏到非预期主机。
     */
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
            .version(HttpClient.Version.HTTP_2)
            .build();

    /**
     * 兜底 HTTP/1.1 客户端。
     * <p>
     * 个别企业代理 / 旧版 Nginx 在 HTTP/2 over TLS 下会返回 GOAWAY 或解析失败；
     * 主请求遇到 {@code java.io.IOException} 且消息匹配 {@code HTTP/2|GOAWAY|http2}
     * 时自动 fallback 到本客户端单次重试。
     */
    private static final HttpClient FALLBACK_HTTP1_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /**
     * 共享调度器：周期性轮询用户取消标志，用以触发流式响应的提前 close。
     * 替代旧实现中"每个请求 new 一个 Thread + Thread.sleep(50)"，
     * 大幅降低空载 CPU 占用，且 200ms 间隔仍能给用户即时取消感。
     */
    private static final ScheduledExecutorService CANCEL_POLLER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "llm-cancel-poller");
        t.setDaemon(true);
        return t;
    });

    private final LlmSettings settings;
    private final HttpClient httpClient;

    public LlmClient(LlmSettings settings) {
        this.settings = settings;
        this.httpClient = SHARED_CLIENT;
    }

    private String resolveApiKey() {
        return SecureKeyStore.loadApiKey(settings == null ? null : settings.apiKey);
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
        String bearer = resolveApiKey();
        if (bearer.isEmpty()) {
            throw new LlmException("API Key 未配置，请在 设置 → 日报生成器设置 中填写");
        }

        int timeout = settings.timeoutSeconds > 0 ? settings.timeoutSeconds : 60;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "text/event-stream")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        log.info("向大模型发起流式请求: endpoint={}, model={}", endpoint, settings.model);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.io.IOException ioEx) {
            if (looksLikeHttp2Issue(ioEx)) {
                log.warn("HTTP/2 失败，降级 HTTP/1.1 重试: {}", ioEx.getMessage());
                try {
                    response = FALLBACK_HTTP1_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                } catch (Exception retryEx) {
                    throw new LlmException("调用大模型失败 (HTTP/1.1 兜底亦失败): " + retryEx.getMessage(), retryEx);
                }
            } else {
                throw new LlmException("调用大模型失败: " + ioEx.getMessage(), ioEx);
            }
        } catch (Exception e) {
            throw new LlmException("调用大模型失败: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = readAll(response.body());
            log.error("大模型返回非成功状态 status={}, body={}", status, truncate(body, 500));
            throw new LlmException("大模型返回错误(HTTP " + status + "): " + truncate(body, 500));
        }

        InputStream body = response.body();
        AtomicBoolean userCancelled = new AtomicBoolean(false);
        ScheduledFuture<?> watchdog = null;
        if (isCancelled != null) {
            watchdog = CANCEL_POLLER.scheduleWithFixedDelay(() -> {
                if (Boolean.TRUE.equals(isCancelled.get()) && !userCancelled.get()) {
                    userCancelled.set(true);
                    try {
                        body.close();
                    } catch (Exception ignored) {
                    }
                }
            }, 200, 200, TimeUnit.MILLISECONDS);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled != null && Boolean.TRUE.equals(isCancelled.get())) {
                    userCancelled.set(true);
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("event:") && line.contains("error")) {
                    log.warn("SSE error event: {}", truncate(line, 200));
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
                    if (obj.has("error") && obj.get("error").isJsonObject()) {
                        String msg = obj.getAsJsonObject("error").has("message")
                                ? obj.getAsJsonObject("error").get("message").getAsString()
                                : data;
                        throw new LlmException("LLM 返回错误: " + truncate(msg, 300));
                    }
                    JsonArray choices = obj.getAsJsonArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        continue;
                    }
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    String delta = extractDelta(choice);
                    if (delta != null && !delta.isEmpty() && onDelta != null) {
                        onDelta.accept(delta);
                    }
                } catch (LlmException llmEx) {
                    throw llmEx;
                } catch (Exception parseEx) {
                    log.warn("解析 SSE 行失败 line={}, err={}", truncate(data, 200), parseEx.getMessage());
                }
            }
        } catch (LlmException llmEx) {
            throw llmEx;
        } catch (Exception e) {
            if (userCancelled.get()) {
                log.info("流式请求已由用户取消");
                return;
            }
            throw new LlmException("读取流式响应失败: " + e.getMessage(), e);
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
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
        String bearer = resolveApiKey();
        if (bearer.isEmpty()) {
            return new PingResult(false, -1, "API Key 未配置");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
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
        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatMessage("system", systemPrompt));
        }
        messages.add(new ChatMessage("user", userContent == null ? "" : userContent));
        return GSON.toJson(new ChatRequest(settings.model, messages, stream));
    }

    /**
     * OpenAI 兼容 chat completions 请求的最小数据模型。把原来散落的
     * {@code Map<String, Object>} 拼装收敛到强类型 record，方便后续扩展
     * {@code temperature}/{@code top_p}/{@code max_tokens} 等可选字段：
     * 新增字段只需在本 record 中追加，Gson 序列化自动覆盖。
     */
    public record ChatMessage(String role, String content) {
    }

    /** Chat Completions request 体。{@code stream=true} 时走 SSE。 */
    public record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 嗅探 IOException 是否是 HTTP/2 协议层问题（连接 GOAWAY / 协商失败 / 帧异常等）。 */
    private static boolean looksLikeHttp2Issue(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String low = msg.toLowerCase(java.util.Locale.ROOT);
                if (low.contains("http/2") || low.contains("http2")
                        || low.contains("goaway") || low.contains("alpn")
                        || low.contains("h2 protocol error")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}
