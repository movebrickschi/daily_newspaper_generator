package io.github.movebrickschi.dailynewspapergenerator.channel;

import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * 通道发送方注册表。按 {@link ChannelConfig.ChannelType} 路由到对应实现。
 *
 * @author Liu Chunchi
 */
public final class ChannelSenderRegistry {

    private static final Map<ChannelConfig.ChannelType, ChannelSender> REGISTRY =
            new EnumMap<>(ChannelConfig.ChannelType.class);

    static {
        register(new FeishuRobotSender());
        register(new DingTalkRobotSender());
        register(new WeComRobotSender());
        register(new GenericWebhookSender());
        register(new DingTalkReportSender());
    }

    private ChannelSenderRegistry() {
    }

    public static void register(ChannelSender sender) {
        REGISTRY.put(sender.supportedType(), sender);
    }

    @NotNull
    public static SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content) {
        if (config.type == null) {
            return SendResult.failure("通道类型未指定");
        }
        ChannelSender sender = REGISTRY.get(config.type);
        if (sender == null) {
            return SendResult.failure("没有找到 " + config.type + " 对应的发送实现");
        }
        try {
            return sender.send(config, title, content);
        } catch (Exception e) {
            return SendResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
