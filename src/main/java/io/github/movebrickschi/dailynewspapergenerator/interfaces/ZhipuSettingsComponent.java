// ZhipuSettingsComponent.java
package io.github.movebrickschi.dailynewspapergenerator.interfaces;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.movebrickschi.dailynewspapergenerator.config.ZhipuSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ZhipuSettingsComponent {
    private final JPanel myMainPanel;
    private final JBTextField myApiKeyText = new JBTextField();
    private final JBTextField myPromptTemplateText = new JBTextField();

    public ZhipuSettingsComponent() {
        try {
            myMainPanel = FormBuilder.createFormBuilder()
                    .addLabeledComponent(new JBLabel("API Key:"), myApiKeyText, 1, false)
                    .addLabeledComponent(new JBLabel("提示词模板:"), myPromptTemplateText, 1, false)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            // 初始化默认值
            ZhipuSettings settings = ZhipuSettings.getInstance();
            if (settings != null) {
                myApiKeyText.setText(settings.apiKey != null ? settings.apiKey : "");
                myPromptTemplateText.setText(settings.promptTemplate != null ? settings.promptTemplate : "");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create settings component", e);
        }
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return myApiKeyText;
    }

    @NotNull
    public String getApiKey() {
        return myApiKeyText.getText() != null ? myApiKeyText.getText() : "";
    }

    @NotNull
    public String getPromptTemplate() {
        return myPromptTemplateText.getText() != null ? myPromptTemplateText.getText() : "";
    }

    public void setApiKey(@NotNull String newText) {
        myApiKeyText.setText(newText != null ? newText : "");
    }

    public void setPromptTemplate(@NotNull String newText) {
        myPromptTemplateText.setText(newText != null ? newText : "");
    }
}
