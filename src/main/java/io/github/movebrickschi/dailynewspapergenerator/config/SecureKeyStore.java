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
     * 加载 API Key，优先取 PasswordSafe 中的；为空时回退到传入的明文（一般是历史配置中遗留值）。
     * 如果 fallback 非空、PasswordSafe 为空，会自动把 fallback 迁移到 PasswordSafe，避免下一次再用明文。
     */
    @NotNull
    public static String loadApiKey(@Nullable String fallbackPlain) {
        String secured = load(KEY_LLM_API_KEY);
        if (!secured.isEmpty()) {
            return secured;
        }
        if (fallbackPlain != null && !fallbackPlain.isEmpty()) {
            store(KEY_LLM_API_KEY, fallbackPlain);
            return fallbackPlain;
        }
        return "";
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
