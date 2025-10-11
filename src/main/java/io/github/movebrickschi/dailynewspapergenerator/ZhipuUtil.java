package io.github.movebrickschi.dailynewspapergenerator;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;

/**
 * ZhipuUtil
 *
 * @author Liu Chunchi
 */
public final class ZhipuUtil {

    private ZhipuUtil() {
    }

    private static final Logger log = LoggerFactory.getLogger(ZhipuUtil.class);

    // 缓存 ZhipuAiClient 实例
    private static volatile ZhipuAiClient cachedClient;

    // API Key 常量
    private static final String API_KEY = "8e26dc7d757047a1a11808de105bbedc.7d77MGRZP178UWwA";

    /**
     * 获取缓存的 ZhipuAiClient 实例
     * @return ZhipuAiClient 实例
     */
    private static ZhipuAiClient getClient() {
        if (cachedClient == null) {
            synchronized (ZhipuUtil.class) {
                if (cachedClient == null) {
                    cachedClient = ZhipuAiClient.builder()
                            .apiKey(API_KEY)
                            .build();
                }
            }
        }
        return cachedClient;
    }

    /**
     * 润色
     * @param content 输入的文本
     * @return 润色后的文本
     */
    public static String polish(String content) {
        try {
            ZhipuAiClient client = getClient();

            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model("glm-4.5v")
                    .messages(Collections.singletonList(
                            ChatMessage.builder()
                                    .role(ChatMessageRole.USER.value())
                                    .content(Arrays.asList(
                                            MessageContent.builder()
                                                    .type("text")
                                                    .text("请将以下Git提交信息润色成更专业的日报格式，保持简洁明了")
                                                    .build(),
                                            MessageContent.builder()
                                                    .type("text")
                                                    .text(content)
                                                    .build()))
                                    .build()))
                    .build();

            ChatCompletionResponse response = client.chat().createChatCompletion(request);
            if (response.isSuccess()) {
                return response.getData().getChoices().getFirst().getMessage().getContent().toString();
            } else {
                log.error("错误: {}", response.getMsg());
                return "AI润色失败: " + response.getMsg();
            }
        } catch (Exception e) {
            log.error("AI润色过程出现异常", e);
            return "AI润色异常: " + e.getMessage();
        }
    }
}

