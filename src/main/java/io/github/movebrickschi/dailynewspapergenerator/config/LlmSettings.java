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
        storages = {
                @Storage("daily-report-settings.xml"),
                @Storage(value = "zhipu-settings.xml", deprecated = true)
        }
)
public class LlmSettings implements PersistentStateComponent<LlmSettings> {

    /**
     * 当前 schema 版本。后续若变更字段语义可在 {@link #loadState(LlmSettings)} 中按版本号迁移。
     */
    public int schemaVersion = 2;

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
        scheduleMigrationIfNeeded();
    }

    /**
     * 旧版本（schemaVersion <= 1）持久化的明文 apiKey 自动迁移到 PasswordSafe。
     * <p>
     * <b>线程</b>：本方法在 IDE 启动加载 PersistentStateComponent 时被同步调用（很可能在 EDT
     * 或 startup BGT 上），不能直接同步访问 {@link com.intellij.ide.passwordSafe.PasswordSafe}：
     * 操作系统钥匙串解锁在某些平台上会弹密码框阻塞调用线程，导致 IDE 启动卡顿。
     * 因此这里只做轻量判断，把真正的 PasswordSafe 写入排到 BGT 异步执行。
     */
    private void scheduleMigrationIfNeeded() {
        if (schemaVersion >= 2 || apiKey == null || apiKey.isBlank()) {
            return;
        }
        final String pending = apiKey;
        apiKey = "";
        schemaVersion = 2;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String secured = SecureKeyStore.load(SecureKeyStore.KEY_LLM_API_KEY);
                if (secured.isEmpty()) {
                    SecureKeyStore.storeApiKey(pending);
                }
            } catch (Throwable ignored) {
                // PasswordSafe 异常不阻塞 IDE 启动，下次保存会再次尝试迁移
            }
        });
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
