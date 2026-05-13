package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.util.messages.Topic;

/**
 * {@link LlmSettings} 变更广播。
 * <p>
 * {@link LlmSettingsConfigurable#apply()} 完成后会 publish 一次本 topic，
 * 已展示的报告对话框 / 推送面板可订阅刷新模板与通道下拉，避免用户改完设置后
 * 仍看到旧列表。
 *
 * @author Liu Chunchi
 */
public interface LlmSettingsListener {

    Topic<LlmSettingsListener> TOPIC =
            Topic.create("DailyReport.LlmSettings.Changed", LlmSettingsListener.class);

    /**
     * settings 已成功保存到磁盘 / PasswordSafe。订阅方应在 EDT 上重读
     * {@link LlmSettings#getInstance()} 并刷新 UI。
     */
    void settingsChanged();
}
