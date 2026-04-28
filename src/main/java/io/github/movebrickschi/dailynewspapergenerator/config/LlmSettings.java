package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用大模型配置属性。
 * 采用 OpenAI 兼容 Chat Completions 协议，支持任意兼容厂商：
 * 智谱 GLM / DeepSeek / 通义千问 / Kimi / OpenAI / Ollama 等。
 *
 * @author Liu Chunchi
 */
@State(
        name = "LlmSettings",
        storages = @Storage("zhipu-settings.xml")
)
public class LlmSettings implements PersistentStateComponent<LlmSettings> {

    /**
     * OpenAI 兼容接口的 Base URL（不带 /chat/completions 后缀）。
     * 默认指向智谱开放平台。
     */
    public String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /**
     * 模型名称。
     */
    public String model = "glm-4.5";

    /**
     * API Key 历史明文字段，仅用于一次性迁移到 {@link SecureKeyStore}。
     * 一旦 PasswordSafe 中已存在凭证或用户保存过设置，本字段会被清空。
     * 不要直接读取，请使用 {@link SecureKeyStore#loadApiKey(String)}。
     */
    public String apiKey = "";

    /**
     * 请求超时秒数。
     */
    public int timeoutSeconds = 60;

    /**
     * 默认提示词模板（兼容旧版本；多模板管理见 {@link #templates}）。
     */
    public String promptTemplate = "请将以下Git提交信息润色成更专业的日报格式，保持简洁明了";

    /**
     * 是否启用流式输出（SSE）。
     */
    public boolean enableStream = true;

    /**
     * 导出 markdown 文件的目标目录。空表示使用项目默认 {@code 项目根/docs/daily}。
     */
    public String outputDir = "";

    /**
     * 多套提示词模板。空时回退到 {@link #promptTemplate}。
     */
    @XCollection(propertyElementName = "templates")
    public List<PromptTemplate> templates = new ArrayList<>();

    /**
     * 当前选中的模板 id（对应 {@link PromptTemplate#id}）。
     */
    public String activeTemplateId = "";

    /**
     * 推送通道列表（飞书 / 钉钉群 / 钉钉日志 / 企微 / 通用 webhook）。
     */
    @XCollection(propertyElementName = "channels")
    public List<ChannelConfig> channels = new ArrayList<>();

    @Nullable
    @Override
    public LlmSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull LlmSettings state) {
        XmlSerializerUtil.copyBean(state, this);
        if (templates == null) {
            templates = new ArrayList<>();
        }
        if (channels == null) {
            channels = new ArrayList<>();
        }
    }

    public static LlmSettings getInstance() {
        return ApplicationManager.getApplication().getService(LlmSettings.class);
    }

    /**
     * 取当前激活的提示词内容。优先用 templates 中匹配 activeTemplateId 的，否则回退到 promptTemplate。
     */
    public String resolveActivePrompt() {
        if (templates != null && activeTemplateId != null && !activeTemplateId.isBlank()) {
            for (PromptTemplate t : templates) {
                if (t != null && activeTemplateId.equals(t.id)) {
                    return t.content == null ? "" : t.content;
                }
            }
        }
        return promptTemplate == null ? "" : promptTemplate;
    }
}
