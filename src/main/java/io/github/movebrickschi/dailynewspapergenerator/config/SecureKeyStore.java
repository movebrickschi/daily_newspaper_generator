package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 凭证安全存储封装。基于 IDE 自带的 {@link PasswordSafe}，
 * 在不同操作系统上分别走系统钥匙串 / Windows 凭据管理器 / KWallet。
 * <p>
 * 命名空间：{@code DailyReportPlugin}，所有 key 都通过该子系统来管理。
 *
 * @author Liu Chunchi
 */
public final class SecureKeyStore {

    /** 顶层子系统名，避免和其它插件冲突。 */
    public static final String SUBSYSTEM = "DailyReportPlugin";

    /** LLM API Key 的逻辑键。 */
    public static final String KEY_LLM_API_KEY = "llm.apiKey";

    private SecureKeyStore() {
    }

    /**
     * 读取凭证。若不存在返回空串。
     */
    @NotNull
    public static String load(@NotNull String key) {
        Credentials creds = PasswordSafe.getInstance().get(attrs(key));
        if (creds == null) {
            return "";
        }
        String pwd = creds.getPasswordAsString();
        return pwd == null ? "" : pwd;
    }

    /**
     * 写入凭证；传 null 或空串等价于删除。
     */
    public static void store(@NotNull String key, @Nullable String value) {
        Credentials creds = (value == null || value.isEmpty())
                ? null : new Credentials(key, value);
        PasswordSafe.getInstance().set(attrs(key), creds);
    }

    /**
     * 加载 API Key（只读）：优先返回 PasswordSafe 中已保存的凭证；为空时回退到传入的 fallback 明文。
     * <p>
     * <b>v1.4 修订</b>：本方法不再因 fallback 非空而自动把 fallback 写入 PasswordSafe。
     * 旧的"自动迁移"语义带来了副作用——在「设置 → 测试连接」时，用户尚未保存的临时输入
     * 会被悄悄持久化。
     * 自动迁移的需求改由 {@link io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings}
     * 在 startup 后异步显式 {@link #storeApiKey(String)} 完成。
     */
    @NotNull
    public static String loadApiKey(@Nullable String fallbackPlain) {
        String secured = load(KEY_LLM_API_KEY);
        if (!secured.isEmpty()) {
            return secured;
        }
        return fallbackPlain == null ? "" : fallbackPlain;
    }

    public static void storeApiKey(@Nullable String value) {
        store(KEY_LLM_API_KEY, value);
    }

    private static CredentialAttributes attrs(@NotNull String key) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SUBSYSTEM, key),
                key
        );
    }
}
