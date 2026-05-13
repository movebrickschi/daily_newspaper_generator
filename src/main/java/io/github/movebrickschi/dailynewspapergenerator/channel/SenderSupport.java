package io.github.movebrickschi.dailynewspapergenerator.channel;

/**
 * {@link ChannelSender} 实现的工具基类，把 5 个 sender 重复出现的
 * 小工具（response body 截断 / 兜底字符串 / errcode 提示）集中到一处。
 *
 * <p>故意保持「无状态、纯静态方法」风格，不强制子类继承——sender 实现仅在
 * 自身静态导入 / 调用本类时受益，已有实现可平滑迁移。
 *
 * @author Liu Chunchi
 */
public final class SenderSupport {

    /** 通用截断长度：HTTP 响应体一般只保留前 200 字符放进 SendResult 反馈。 */
    public static final int DEFAULT_TRUNCATE = 200;

    private SenderSupport() {
    }

    /** 截断长字符串便于在通知 / 状态栏中展示，超长部分以 "..." 替代。 */
    public static String truncate(String s) {
        return truncate(s, DEFAULT_TRUNCATE);
    }

    /** 与 {@link #truncate(String)} 等价但允许自定义阈值。 */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 返回第一个非空白的候选字符串；全部为空时返回空串。便于在 sender 里组合：
     * {@code emptyToDefault(title, config.title, "日报")}。
     */
    public static String emptyToDefault(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return "";
    }
}
