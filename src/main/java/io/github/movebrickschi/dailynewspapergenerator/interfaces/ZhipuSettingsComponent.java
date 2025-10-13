// ZhipuSettingsComponent.java
package io.github.movebrickschi.dailynewspapergenerator.interfaces;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.movebrickschi.dailynewspapergenerator.config.ZhipuSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * 设置界面
 *
 * @author Liu Chunchi
 */
public class ZhipuSettingsComponent {
    private final JPanel myMainPanel;
    private final JBTextField myApiKeyText = new JBTextField();
    private final JTextArea myPromptTemplateText = new JTextArea(6, 50);
    // 添加重置按钮
    private final JButton myResetButton = new JButton("恢复默认设置");

    // 默认值常量
    private static final String DEFAULT_API_KEY = "8e26dc7d757047a1a11808de105bbedc.7d77MGRZP178UWwA";
    private static final String DEFAULT_PROMPT_TEMPLATE = "请将以下Git提交信息润色成更专业的日报格式，保持简洁明了";

    public ZhipuSettingsComponent() {
        try {
            // 配置提示词文本区域
            myPromptTemplateText.setLineWrap(true);
            myPromptTemplateText.setWrapStyleWord(true);
//            myPromptTemplateText.setBorder(myApiKeyText.getBorder());

            // 启用手动调整大小
//            myPromptTemplateText.setPreferredSize(new Dimension(400, 120));
//            myPromptTemplateText.setMinimumSize(new Dimension(200, 60));

            // 创建可滚动面板
            JScrollPane scrollPane = new JBScrollPane(myPromptTemplateText);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setPreferredSize(new Dimension(400, 120));
            scrollPane.setMinimumSize(new Dimension(200, 60));

            // 配置重置按钮事件
            myResetButton.addActionListener(e -> resetToDefaults());

            myMainPanel = FormBuilder.createFormBuilder()
                    .addLabeledComponent(new JBLabel("API Key（智谱）:"), myApiKeyText, 1, false)
                    .addLabeledComponent(new JBLabel("提示词模板:"), scrollPane, 1, false)
                    .addLabeledComponent(new JBLabel(""), myResetButton, 1, false) // 添加重置按钮
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            // 初始化默认值
            ZhipuSettings settings = ZhipuSettings.getInstance();
            if (settings != null) {
                myApiKeyText.setText(settings.apiKey != null ? settings.apiKey : DEFAULT_API_KEY);
                myPromptTemplateText.setText(settings.promptTemplate != null ? settings.promptTemplate : DEFAULT_PROMPT_TEMPLATE);
            } else {
                // 如果无法获取设置，使用默认值
                myApiKeyText.setText(DEFAULT_API_KEY);
                myPromptTemplateText.setText(DEFAULT_PROMPT_TEMPLATE);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create settings component", e);
        }
    }

    // 重置为默认设置的方法
    private void resetToDefaults() {
        myApiKeyText.setText(DEFAULT_API_KEY);
        myPromptTemplateText.setText(DEFAULT_PROMPT_TEMPLATE);
    }

    // 更新相关方法以适应 JTextArea
    @NotNull
    public String getPromptTemplate() {
        return myPromptTemplateText.getText() != null ? myPromptTemplateText.getText() : "";
    }

    public void setPromptTemplate(@NotNull String newText) {
        myPromptTemplateText.setText(newText != null ? newText : "");
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

    public void setApiKey(@NotNull String newText) {
        myApiKeyText.setText(newText != null ? newText : "");
    }
}
