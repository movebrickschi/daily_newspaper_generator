// ZhipuSettingsConfigurable.java
package io.github.movebrickschi.dailynewspapergenerator;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import io.github.movebrickschi.dailynewspapergenerator.config.ZhipuSettings;
import io.github.movebrickschi.dailynewspapergenerator.interfaces.ZhipuSettingsComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 设置配置
 *
 * @author Liu Chunchi
 */
public class ZhipuSettingsConfigurable implements Configurable {
    private ZhipuSettingsComponent mySettingsComponent;

    public ZhipuSettingsConfigurable() {
        // 确保有一个无参构造函数
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "日报生成器设置";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent != null ? mySettingsComponent.getPreferredFocusedComponent() : null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (mySettingsComponent == null) {
            mySettingsComponent = new ZhipuSettingsComponent();
        }
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        if (mySettingsComponent == null) return false;
        ZhipuSettings settings = ZhipuSettings.getInstance();
        // 添加空值检查
        if (settings == null) return false;

        return !mySettingsComponent.getApiKey().equals(settings.apiKey) ||
                !mySettingsComponent.getPromptTemplate().equals(settings.promptTemplate);
    }

    @Override
    public void apply() throws ConfigurationException {
        if (mySettingsComponent == null) return;
        ZhipuSettings settings = ZhipuSettings.getInstance();
        // 添加空值检查
        if (settings == null) return;
        settings.apiKey = mySettingsComponent.getApiKey();
        settings.promptTemplate = mySettingsComponent.getPromptTemplate();
    }

    @Override
    public void reset() {
        if (mySettingsComponent == null) return;
        ZhipuSettings settings = ZhipuSettings.getInstance();
        // 添加空值检查
        if (settings == null) return;
        mySettingsComponent.setPromptTemplate(settings.promptTemplate);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }
}
