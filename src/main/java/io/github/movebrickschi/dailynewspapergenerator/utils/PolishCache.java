package io.github.movebrickschi.dailynewspapergenerator.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 非流式润色结果的进程内 LRU 缓存。
 *
 * <p>命中键 = {@code sha256(model | prompt | content)}。命中即跳过一次 LLM 调用，
 * 避免用户在同一份 commit 文本上反复点「再润色」时浪费 token。</p>
 *
 * <p><b>仅适用于非流式调用</b>：流式过程中用户可能取消、看到不完整结果，缓存会破坏直觉。
 * 调用方在流式分支应直接走 LlmClient，不要进这里。</p>
 *
 * @author Liu Chunchi
 */
public final class PolishCache {

    /** 进程内最大缓存条目数（LRU 淘汰）。中文日报正文通常几 KB，16 条 ≈ 100KB。 */
    private static final int MAX_ENTRIES = 16;

    private static final Map<String, String> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private PolishCache() {
    }

    /**
     * 命中即返回；未命中调用 {@code producer} 并把非空非错误结果写入缓存。
     * <p>认为以「AI润色异常」开头的字符串是失败回执，不缓存（避免错误结果污染下次）。
     *
     * @param model    当前模型名（区分 GLM/DeepSeek 等）
     * @param prompt   系统提示词
     * @param content  用户内容（commit 文本）
     * @param producer 缓存未命中时的实际生成器
     * @return 缓存或新生成的文本
     */
    @NotNull
    public static String getOrCompute(@Nullable String model,
                                      @Nullable String prompt,
                                      @Nullable String content,
                                      @NotNull Supplier<String> producer) {
        String key = key(model, prompt, content);
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String fresh = producer.get();
        if (fresh != null && !fresh.isBlank() && !fresh.startsWith("AI润色异常")) {
            CACHE.put(key, fresh);
        }
        return fresh == null ? "" : fresh;
    }

    /** 清空缓存。供测试与设置变更后调用（如换模型）。 */
    public static void clear() {
        CACHE.clear();
    }

    /** 仅用于调试。 */
    public static int size() {
        return CACHE.size();
    }

    private static String key(String model, String prompt, String content) {
        String joined = (model == null ? "" : model) + "|"
                + (prompt == null ? "" : prompt) + "|"
                + (content == null ? "" : content);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // 任意 JRE 必带 SHA-256；走到这里只能回退到普通 hashCode
            return Integer.toHexString(joined.hashCode());
        }
    }
}
