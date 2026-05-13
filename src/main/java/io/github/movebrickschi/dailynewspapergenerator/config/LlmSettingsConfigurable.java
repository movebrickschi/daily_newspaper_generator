package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import io.github.movebrickschi.dailynewspapergenerator.i18n.DailyReportBundle;
import io.github.movebrickschi.dailynewspapergenerator.interfaces.LlmSettingsComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * 日报生成器设置入口。
 *
 * @author Liu Chunchi
 */
public class LlmSettingsConfigurable implements Configurable {

    private LlmSettingsComponent mySettingsComponent;

    public LlmSettingsConfigurable() {
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return DailyReportBundle.message("settings.title");
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent != null ? mySettingsComponent.getPreferredFocusedComponent() : null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (mySettingsComponent == null) {
            mySettingsComponent = new LlmSettingsComponent();
        }
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        if (mySettingsComponent == null) {
            return false;
        }
        LlmSettings s = LlmSettings.getInstance();
        if (s == null) {
            return false;
        }
        String savedKey = SecureKeyStore.loadApiKey(s.apiKey);
        return !safeEquals(mySettingsComponent.getBaseUrl(), s.baseUrl)
                || !safeEquals(mySettingsComponent.getModel(), s.model)
                || !safeEquals(mySettingsComponent.getApiKey(), savedKey)
                || mySettingsComponent.getTimeoutSeconds() != s.timeoutSeconds
                || !safeEquals(mySettingsComponent.getPromptTemplate(), s.promptTemplate)
                || mySettingsComponent.isEnableStream() != s.enableStream
                || !safeEquals(mySettingsComponent.getOutputDir(), s.outputDir)
                || !safeEquals(mySettingsComponent.getActiveTemplateId(), s.activeTemplateId)
                || !safeListEquals(mySettingsComponent.getTemplates(), s.templates)
                || !safeListEquals(mySettingsComponent.getChannels(), s.channels)
                || mySettingsComponent.hasChannelSecretChanges();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (mySettingsComponent == null) {
            return;
        }
        LlmSettings s = LlmSettings.getInstance();
        if (s == null) {
            return;
        }
        s.baseUrl = mySettingsComponent.getBaseUrl();
        s.model = mySettingsComponent.getModel();
        SecureKeyStore.storeApiKey(mySettingsComponent.getApiKey());
        // 保存后清空持久化文件中的明文，避免下一次启动还能读出旧值
        s.apiKey = "";
        s.timeoutSeconds = mySettingsComponent.getTimeoutSeconds();
        s.promptTemplate = mySettingsComponent.getPromptTemplate();
        s.enableStream = mySettingsComponent.isEnableStream();
        s.outputDir = mySettingsComponent.getOutputDir();
        s.activeTemplateId = mySettingsComponent.getActiveTemplateId();
        s.templates = new ArrayList<>(mySettingsComponent.getTemplates());
        s.channels = new ArrayList<>(mySettingsComponent.getChannels());
        for (String key : mySettingsComponent.getDeletedChannelSecretKeys()) {
            SecureKeyStore.store(key, null);
        }
        for (Map.Entry<String, String> entry : mySettingsComponent.getChannelSecretDrafts().entrySet()) {
            SecureKeyStore.store(entry.getKey(), entry.getValue());
        }
        // 通知所有已订阅的 UI 重读最新 settings 并刷新模板 / 通道下拉。
        try {
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(LlmSettingsListener.TOPIC)
                    .settingsChanged();
        } catch (Throwable ignored) {
            // MessageBus 不可用（极少见，如测试环境 LightApplication）不应阻断保存
        }
    }

    @Override
    public void reset() {
        if (mySettingsComponent == null) {
            return;
        }
        LlmSettings s = LlmSettings.getInstance();
        if (s == null) {
            return;
        }
        mySettingsComponent.setBaseUrl(s.baseUrl != null ? s.baseUrl : "");
        mySettingsComponent.setModel(s.model != null ? s.model : "");
        mySettingsComponent.setApiKey(SecureKeyStore.loadApiKey(s.apiKey));
        mySettingsComponent.setTimeoutSeconds(s.timeoutSeconds);
        mySettingsComponent.setPromptTemplate(s.promptTemplate != null ? s.promptTemplate : "");
        mySettingsComponent.setEnableStream(s.enableStream);
        mySettingsComponent.setOutputDir(s.outputDir != null ? s.outputDir : "");
        mySettingsComponent.setTemplates(s.templates, s.activeTemplateId);
        mySettingsComponent.setChannels(s.channels);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    private static boolean safeEquals(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private static boolean safeListEquals(java.util.List<?> a, java.util.List<?> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Object oa = a.get(i);
            Object ob = b.get(i);
            if (oa == null ? ob != null : !oa.equals(ob)) return false;
        }
        return true;
    }
}
