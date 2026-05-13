package io.github.movebrickschi.dailynewspapergenerator.ui;

import org.jetbrains.annotations.NotNull;

/**
 * {@link MarkdownEngine} 全局工厂 / 注册点。
 *
 * <p>默认走 {@link MarkdownRenderer#toHtml(String)}（轻量正则实现，覆盖 h1~h6 /
 * 列表 / 表格 / 链接 / 代码块等常见场景）。第三方扩展可在启动时调用
 * {@link #setActive(MarkdownEngine)} 注入更完整的引擎。
 *
 * <p>本注册点故意不走 application service：避免 UI 层组件在初始化前依赖 service 树，
 * 同时让单元测试可以方便地 set/reset。
 *
 * @author Liu Chunchi
 */
public final class MarkdownEngines {

    private static volatile MarkdownEngine active = MarkdownRenderer::toHtml;

    private MarkdownEngines() {
    }

    /** 当前生效的引擎，永远非空。 */
    @NotNull
    public static MarkdownEngine current() {
        return active;
    }

    /** 注册新引擎，会替代默认实现。传 {@code null} 表示恢复默认。 */
    public static void setActive(MarkdownEngine engine) {
        active = engine != null ? engine : MarkdownRenderer::toHtml;
    }
}
