package io.github.movebrickschi.dailynewspapergenerator.utils;

import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import io.github.movebrickschi.dailynewspapergenerator.llm.LlmClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 大模型润色工具类（通用 OpenAI 兼容协议）。
 *
 * @author Liu Chunchi
 */
public final class LlmUtil {

    private LlmUtil() {
    }

    private static final Logger log = LoggerFactory.getLogger(LlmUtil.class);

    /**
     * 阻塞式润色：内部走流式累加。
     *
     * @param content 原始 Git 提交记录文本
     * @return 润色后的文本；若失败则返回对用户友好的错误字符串
     */
    public static String polish(String content) {
        return polish(content, null);
    }

    /**
     * 阻塞式润色，可显式指定提示词模板。
     *
     * @param content        原始内容
     * @param promptOverride 提示词模板（为空则使用配置中的默认模板）
     */
    public static String polish(String content, String promptOverride) {
        LlmSettings settings = LlmSettings.getInstance();
        String error = validate(settings);
        if (error != null) {
            return error;
        }
        String prompt = (promptOverride == null || promptOverride.isBlank())
                ? settings.promptTemplate : promptOverride;
        return PolishCache.getOrCompute(settings.model, prompt, content, () -> {
            try {
                log.info("正在调用大模型润色 model={} (cache miss)", settings.model);
                return new LlmClient(settings).chat(prompt, content);
            } catch (Exception e) {
                log.error("AI润色过程出现异常", e);
                return "AI润色异常: " + e.getMessage();
            }
        });
    }

    /**
     * 流式润色。每接收一段 delta 调用 onDelta；isCancelled 返回 true 时尽快中止。
     *
     * @param content        原始内容
     * @param promptOverride 提示词模板（为空则使用配置中的默认模板）
     * @param onDelta        增量回调
     * @param isCancelled    取消信号（可为空）
     * @return 调用是否成功（false 时错误信息已通过 onDelta 推送）
     */
    public static boolean polishStream(String content,
                                       String promptOverride,
                                       Consumer<String> onDelta,
                                       Supplier<Boolean> isCancelled) {
        LlmSettings settings = effectiveSettings();
        String error = validate(settings);
        if (error != null) {
            if (onDelta != null) {
                onDelta.accept(error);
            }
            return false;
        }
        try {
            log.info("正在以流式方式调用大模型润色 model={}", settings.model);
            String prompt = (promptOverride == null || promptOverride.isBlank())
                    ? settings.promptTemplate : promptOverride;
            new LlmClient(settings).chatStream(prompt, content, onDelta, isCancelled);
            return true;
        } catch (Exception e) {
            log.error("AI流式润色过程出现异常", e);
            if (onDelta != null) {
                onDelta.accept("\n\n[AI润色异常] " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * @deprecated 历史 API。返回 {@link LlmSettings#getInstance()} 本身，
     * 不再复制并写入明文 apiKey 字段（旧实现会让 API Key 长时间漂浮在 heap）。
     * 调用方应直接使用 {@link LlmSettings#getInstance()}，API Key 由
     * {@link LlmClient} 在发请求前一次性 {@link SecureKeyStore#loadApiKey(String)} 解码。
     */
    @Deprecated
    @Nullable
    public static LlmSettings effectiveSettings() {
        return LlmSettings.getInstance();
    }

    private static String validate(LlmSettings settings) {
        if (settings == null) {
            return "AI润色失败: 无法加载配置";
        }
        String key = SecureKeyStore.loadApiKey(settings.apiKey);
        if (key == null || key.isBlank()) {
            return "AI润色失败: 未配置 API Key，请在 设置 -> 日报生成器设置 中填写";
        }
        if (settings.baseUrl == null || settings.baseUrl.isBlank()) {
            return "AI润色失败: 未配置 Base URL，请在 设置 -> 日报生成器设置 中填写";
        }
        if (settings.model == null || settings.model.isBlank()) {
            return "AI润色失败: 未配置 Model，请在 设置 -> 日报生成器设置 中填写";
        }
        return null;
    }
}
