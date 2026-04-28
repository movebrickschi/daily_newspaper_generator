package io.github.movebrickschi.dailynewspapergenerator.utils;

import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import io.github.movebrickschi.dailynewspapergenerator.llm.LlmClient;
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
        LlmSettings settings = effectiveSettings();
        String error = validate(settings);
        if (error != null) {
            return error;
        }
        try {
            log.info("正在调用大模型润色 model={}", settings.model);
            String prompt = (promptOverride == null || promptOverride.isBlank())
                    ? settings.promptTemplate : promptOverride;
            return new LlmClient(settings).chat(prompt, content);
        } catch (Exception e) {
            log.error("AI润色过程出现异常", e);
            return "AI润色异常: " + e.getMessage();
        }
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
     * 加载有效配置：把 PasswordSafe 中的 API Key 注入到一个临时副本里，避免改动持久态。
     */
    public static LlmSettings effectiveSettings() {
        LlmSettings persisted = LlmSettings.getInstance();
        if (persisted == null) {
            return null;
        }
        LlmSettings copy = new LlmSettings();
        copy.baseUrl = persisted.baseUrl;
        copy.model = persisted.model;
        copy.timeoutSeconds = persisted.timeoutSeconds;
        copy.promptTemplate = persisted.promptTemplate;
        copy.enableStream = persisted.enableStream;
        copy.outputDir = persisted.outputDir;
        copy.apiKey = SecureKeyStore.loadApiKey(persisted.apiKey);
        return copy;
    }

    private static String validate(LlmSettings settings) {
        if (settings == null) {
            return "AI润色失败: 无法加载配置";
        }
        if (settings.apiKey == null || settings.apiKey.isBlank()) {
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
