package io.github.movebrickschi.dailynewspapergenerator.channel;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Sender 间共享的 HTTP 工具。
 * <p>
 * {@link #CLIENT} 注入独立 {@link CookieManager}（拒收所有 cookie），
 * 避免被 JVM 默认 {@code CookieHandler} 接管而在 IDE 进程内累积 cookie 状态。
 *
 * @author Liu Chunchi
 */
final class HttpUtil {

    /** 单次请求默认超时（秒）。可通过 JVM 系统属性 {@code dailyreport.http.timeout} 覆盖。 */
    private static final long DEFAULT_REQUEST_TIMEOUT_SEC = readSystemTimeout(
            "dailyreport.http.timeout", 15);

    /** 连接默认超时（秒）。可通过 {@code dailyreport.http.connectTimeout} 覆盖。 */
    private static final long DEFAULT_CONNECT_TIMEOUT_SEC = readSystemTimeout(
            "dailyreport.http.connectTimeout", 5);

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SEC))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
            .build();

    private HttpUtil() {
    }

    static HttpResponse<String> postJson(String url, String json) throws Exception {
        return postJson(url, json, Map.of());
    }

    static HttpResponse<String> postJson(String url, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SEC))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SEC))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static long readSystemTimeout(String prop, long fallback) {
        String raw = System.getProperty(prop);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
