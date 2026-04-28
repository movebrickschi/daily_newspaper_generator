package io.github.movebrickschi.dailynewspapergenerator.channel;

import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import org.jetbrains.annotations.NotNull;

/**
 * 推送通道发送方接口。
 *
 * @author Liu Chunchi
 */
public interface ChannelSender {

    /** 该 sender 支持的 channel 类型。 */
    ChannelConfig.ChannelType supportedType();

    /**
     * 发送一份内容到指定通道。
     *
     * @param config  通道配置（含 webhook URL、模板 id 等明文字段）
     * @param title   报告标题
     * @param content 报告 markdown 内容
     */
    @NotNull
    SendResult send(@NotNull ChannelConfig config, @NotNull String title, @NotNull String content);
}
