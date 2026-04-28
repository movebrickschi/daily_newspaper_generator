package io.github.movebrickschi.dailynewspapergenerator.interfaces;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.PromptTemplate;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;
import io.github.movebrickschi.dailynewspapergenerator.llm.LlmClient;
import io.github.movebrickschi.dailynewspapergenerator.ui.ChannelListPanel;
import io.github.movebrickschi.dailynewspapergenerator.ui.PromptTemplateListPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大模型设置界面。
 * 支持通过选择厂商预设快速填充 Base URL 和 Model。
 *
 * @author Liu Chunchi
 */
public class LlmSettingsComponent {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String DEFAULT_MODEL = "glm-4.5";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final String DEFAULT_PROMPT_TEMPLATE =
            "请将以下Git提交信息润色成更专业的日报格式，保持简洁明了";
    private static final String CUSTOM_PRESET = "自定义";

    private static final Map<String, String[]> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("智谱 GLM",        new String[]{"https://open.bigmodel.cn/api/paas/v4",             "glm-4.5"});
        PRESETS.put("DeepSeek",        new String[]{"https://api.deepseek.com/v1",                      "deepseek-chat"});
        PRESETS.put("通义千问 Qwen",    new String[]{"https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"});
        PRESETS.put("Kimi (Moonshot)", new String[]{"https://api.moonshot.cn/v1",                       "moonshot-v1-8k"});
        PRESETS.put("OpenAI",          new String[]{"https://api.openai.com/v1",                        "gpt-4o-mini"});
        PRESETS.put("Ollama (本地)",    new String[]{"http://localhost:11434/v1",                        "llama3"});
    }

    private final JPanel myMainPanel;
    private final ComboBox<String> myProviderPreset = new ComboBox<>();
    private final JBTextField myBaseUrlText = new JBTextField();
    private final JBTextField myModelText = new JBTextField();
    private final JBPasswordField myApiKeyField = new JBPasswordField();
    private final JButton myToggleApiKeyVisibility = new JButton("显示");
    private final JButton myTestConnectionButton = new JButton("测试连接");
    private final JBTextField myTimeoutText = new JBTextField();
    private final JBCheckBox myEnableStreamCheck = new JBCheckBox("启用流式输出（边生成边显示）", true);
    private final JBTextField myOutputDirText = new JBTextField();
    private final JTextArea myPromptTemplateText = new JTextArea(6, 50);
    private final JButton myResetButton = new JButton("恢复默认设置");

    private final PromptTemplateListPanel myTemplateListPanel = new PromptTemplateListPanel();
    private final ChannelListPanel myChannelListPanel = new ChannelListPanel();

    /** 默认是否显示 API Key 明文。 */
    private boolean apiKeyVisible = false;
    private final char defaultEchoChar;

    private boolean suppressPresetEvents = false;

    public LlmSettingsComponent() {
        try {
            defaultEchoChar = myApiKeyField.getEchoChar();
            applyApiKeyEcho();

            myToggleApiKeyVisibility.addActionListener(e -> {
                apiKeyVisible = !apiKeyVisible;
                applyApiKeyEcho();
                myToggleApiKeyVisibility.setText(apiKeyVisible ? "隐藏" : "显示");
            });
            myTestConnectionButton.addActionListener(e -> runTestConnection());

            myPromptTemplateText.setLineWrap(true);
            myPromptTemplateText.setWrapStyleWord(true);

            JScrollPane promptScroll = new JBScrollPane(myPromptTemplateText);
            promptScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            promptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            promptScroll.setPreferredSize(new Dimension(400, 120));
            promptScroll.setMinimumSize(new Dimension(200, 60));

            myProviderPreset.addItem(CUSTOM_PRESET);
            for (String name : PRESETS.keySet()) {
                myProviderPreset.addItem(name);
            }
            myProviderPreset.addActionListener(e -> {
                if (suppressPresetEvents) {
                    return;
                }
                Object selected = myProviderPreset.getSelectedItem();
                if (selected == null || CUSTOM_PRESET.equals(selected)) {
                    return;
                }
                String[] preset = PRESETS.get(selected.toString());
                if (preset != null) {
                    myBaseUrlText.setText(preset[0]);
                    myModelText.setText(preset[1]);
                }
            });

            myResetButton.addActionListener(e -> resetToDefaults());

            JPanel apiKeyPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
            apiKeyPanel.add(myApiKeyField, BorderLayout.CENTER);

            JPanel apiKeyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
            apiKeyButtons.add(myToggleApiKeyVisibility);
            apiKeyButtons.add(myTestConnectionButton);
            apiKeyPanel.add(apiKeyButtons, BorderLayout.EAST);

            JBLabel hint = new JBLabel(
                    "<html>通用 OpenAI 兼容协议 —— 仅需填写 Base URL、API Key、Model 即可接入任意兼容厂商。<br/>"
                            + "API Key 安全保存在系统钥匙串（Keychain / Credential Manager）。</html>");

            JPanel basicPanel = FormBuilder.createFormBuilder()
                    .addComponent(hint)
                    .addLabeledComponent(new JBLabel("厂商预设:"), myProviderPreset, 1, false)
                    .addLabeledComponent(new JBLabel("Base URL:"), myBaseUrlText, 1, false)
                    .addLabeledComponent(new JBLabel("Model:"), myModelText, 1, false)
                    .addLabeledComponent(new JBLabel("API Key:"), apiKeyPanel, 1, false)
                    .addLabeledComponent(new JBLabel("超时(秒):"), myTimeoutText, 1, false)
                    .addComponent(myEnableStreamCheck)
                    .addLabeledComponent(new JBLabel("导出目录:"), myOutputDirText, 1, false)
                    .addLabeledComponent(new JBLabel("默认提示词:"), promptScroll, 1, false)
                    .addLabeledComponent(new JBLabel(""), myResetButton, 1, false)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            JBTabbedPane tabs = new JBTabbedPane();
            tabs.addTab("基础", basicPanel);
            tabs.addTab("提示词模板", myTemplateListPanel.getRoot());
            tabs.addTab("推送通道", myChannelListPanel.getRoot());

            myMainPanel = new JPanel(new BorderLayout());
            myMainPanel.add(tabs, BorderLayout.CENTER);

            LlmSettings settings = LlmSettings.getInstance();
            if (settings != null) {
                setBaseUrl(nonNullOrDefault(settings.baseUrl, DEFAULT_BASE_URL));
                setModel(nonNullOrDefault(settings.model, DEFAULT_MODEL));
                setApiKey(SecureKeyStore.loadApiKey(settings.apiKey));
                setTimeoutSeconds(settings.timeoutSeconds > 0 ? settings.timeoutSeconds : DEFAULT_TIMEOUT_SECONDS);
                setPromptTemplate(nonNullOrDefault(settings.promptTemplate, DEFAULT_PROMPT_TEMPLATE));
                setEnableStream(settings.enableStream);
                setOutputDir(settings.outputDir == null ? "" : settings.outputDir);
                setTemplates(settings.templates, settings.activeTemplateId);
                setChannels(settings.channels);
            } else {
                resetToDefaults();
            }
            syncPresetFromFields();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create settings component", e);
        }
    }

    private void applyApiKeyEcho() {
        myApiKeyField.setEchoChar(apiKeyVisible ? (char) 0 : defaultEchoChar);
    }

    private void runTestConnection() {
        // 临时构造一个 settings 副本（不写入持久态），用当前 UI 上的值发起 ping。
        LlmSettings probe = new LlmSettings();
        probe.baseUrl = getBaseUrl();
        probe.model = getModel();
        probe.apiKey = getApiKey();
        probe.timeoutSeconds = getTimeoutSeconds();

        if (probe.apiKey == null || probe.apiKey.isBlank()) {
            notify(NotificationType.WARNING, "测试连接", "请先填写 API Key");
            return;
        }
        if (probe.baseUrl == null || probe.baseUrl.isBlank()) {
            notify(NotificationType.WARNING, "测试连接", "请先填写 Base URL");
            return;
        }
        myTestConnectionButton.setEnabled(false);
        String originalText = myTestConnectionButton.getText();
        myTestConnectionButton.setText("测试中...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            LlmClient.PingResult result;
            try {
                result = new LlmClient(probe).ping();
            } catch (Exception ex) {
                result = new LlmClient.PingResult(false, -1, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            final LlmClient.PingResult finalResult = result;
            ApplicationManager.getApplication().invokeLater(() -> {
                myTestConnectionButton.setEnabled(true);
                myTestConnectionButton.setText(originalText);
                if (finalResult.ok()) {
                    notify(NotificationType.INFORMATION, "测试连接成功",
                            "HTTP " + finalResult.status() + "，Base URL 与凭证可用。");
                } else if (finalResult.status() == 401 || finalResult.status() == 403) {
                    notify(NotificationType.ERROR, "测试连接失败 (鉴权失败)",
                            "HTTP " + finalResult.status() + "，请检查 API Key 是否正确。\n" + finalResult.message());
                } else if (finalResult.status() == 404) {
                    notify(NotificationType.ERROR, "测试连接失败 (404)",
                            "Base URL 可能不正确或 Model 在该服务上不存在。\n" + finalResult.message());
                } else if (finalResult.status() == -1) {
                    notify(NotificationType.ERROR, "测试连接失败",
                            "无法连接到服务，请检查网络或 Base URL：\n" + finalResult.message());
                } else {
                    notify(NotificationType.ERROR, "测试连接失败",
                            "HTTP " + finalResult.status() + "：" + finalResult.message());
                }
            });
        });
    }

    private static void notify(NotificationType type, String title, String content) {
        Notification n = new Notification("DailyReportGroup", title, content, type);
        Notifications.Bus.notify(n);
    }

    private static String nonNullOrDefault(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }

    private void resetToDefaults() {
        setBaseUrl(DEFAULT_BASE_URL);
        setModel(DEFAULT_MODEL);
        setApiKey("");
        setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        setEnableStream(true);
        setOutputDir("");
        syncPresetFromFields();
    }

    private void syncPresetFromFields() {
        String baseUrl = myBaseUrlText.getText();
        String model = myModelText.getText();
        String match = CUSTOM_PRESET;
        for (Map.Entry<String, String[]> e : PRESETS.entrySet()) {
            if (e.getValue()[0].equalsIgnoreCase(baseUrl) && e.getValue()[1].equalsIgnoreCase(model)) {
                match = e.getKey();
                break;
            }
        }
        suppressPresetEvents = true;
        try {
            myProviderPreset.setSelectedItem(match);
        } finally {
            suppressPresetEvents = false;
        }
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return myApiKeyField;
    }

    @NotNull
    public String getBaseUrl() {
        String v = myBaseUrlText.getText();
        return v == null ? "" : v;
    }

    public void setBaseUrl(@NotNull String v) {
        myBaseUrlText.setText(v == null ? "" : v);
    }

    @NotNull
    public String getModel() {
        String v = myModelText.getText();
        return v == null ? "" : v;
    }

    public void setModel(@NotNull String v) {
        myModelText.setText(v == null ? "" : v);
    }

    @NotNull
    public String getApiKey() {
        char[] arr = myApiKeyField.getPassword();
        return arr == null ? "" : new String(arr);
    }

    public void setApiKey(@NotNull String v) {
        myApiKeyField.setText(v == null ? "" : v);
    }

    public int getTimeoutSeconds() {
        try {
            int v = Integer.parseInt(myTimeoutText.getText().trim());
            return v > 0 ? v : DEFAULT_TIMEOUT_SECONDS;
        } catch (Exception e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public void setTimeoutSeconds(int v) {
        myTimeoutText.setText(String.valueOf(v > 0 ? v : DEFAULT_TIMEOUT_SECONDS));
    }

    @NotNull
    public String getPromptTemplate() {
        String v = myPromptTemplateText.getText();
        return v == null ? "" : v;
    }

    public void setPromptTemplate(@NotNull String v) {
        myPromptTemplateText.setText(v == null ? "" : v);
    }

    public boolean isEnableStream() {
        return myEnableStreamCheck.isSelected();
    }

    public void setEnableStream(boolean v) {
        myEnableStreamCheck.setSelected(v);
    }

    @NotNull
    public String getOutputDir() {
        String v = myOutputDirText.getText();
        return v == null ? "" : v.trim();
    }

    public void setOutputDir(@NotNull String v) {
        myOutputDirText.setText(v == null ? "" : v);
    }

    @NotNull
    public List<PromptTemplate> getTemplates() {
        return myTemplateListPanel.getData();
    }

    public void setTemplates(List<PromptTemplate> templates, String activeTemplateId) {
        myTemplateListPanel.setData(copyTemplates(templates), activeTemplateId);
    }

    @NotNull
    public String getActiveTemplateId() {
        String v = myTemplateListPanel.getActiveId();
        return v == null ? "" : v;
    }

    @NotNull
    public List<ChannelConfig> getChannels() {
        return myChannelListPanel.getData();
    }

    public void setChannels(List<ChannelConfig> channels) {
        myChannelListPanel.setData(copyChannels(channels));
    }

    @NotNull
    public Map<String, String> getChannelSecretDrafts() {
        return myChannelListPanel.getSecretDrafts();
    }

    @NotNull
    public Set<String> getDeletedChannelSecretKeys() {
        return myChannelListPanel.getDeletedSecretKeys();
    }

    public boolean hasChannelSecretChanges() {
        return myChannelListPanel.hasSecretChanges();
    }

    private static List<PromptTemplate> copyTemplates(List<PromptTemplate> src) {
        List<PromptTemplate> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (PromptTemplate t : src) {
            if (t == null) {
                continue;
            }
            PromptTemplate c = new PromptTemplate();
            c.id = t.id;
            c.name = t.name;
            c.content = t.content;
            out.add(c);
        }
        return out;
    }

    private static List<ChannelConfig> copyChannels(List<ChannelConfig> src) {
        List<ChannelConfig> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (ChannelConfig ch : src) {
            if (ch == null) {
                continue;
            }
            ChannelConfig c = new ChannelConfig();
            c.id = ch.id;
            c.name = ch.name;
            c.type = ch.type;
            c.webhookUrl = ch.webhookUrl;
            c.genericTemplate = ch.genericTemplate;
            c.title = ch.title;
            c.dingAppKey = ch.dingAppKey;
            c.dingTemplateId = ch.dingTemplateId;
            c.dingMainField = ch.dingMainField;
            c.dingToUserIds = ch.dingToUserIds;
            c.dingToChat = ch.dingToChat;
            out.add(c);
        }
        return out;
    }
}
