package io.github.movebrickschi.dailynewspapergenerator.ui;

/**
 * Markdown → HTML 渲染引擎抽象。
 *
 * <p>默认实现走 {@link MarkdownRenderer} 的内置正则渲染；
 * 若未来需要接入更完整的 markdown 库（{@code commonmark-java} / {@code flexmark} /
 * IntelliJ 自带 {@code org.intellij.markdown}），只需提供一个新的 {@link MarkdownEngine}
 * 并通过 {@link MarkdownEngines#setActive(MarkdownEngine)} 注入即可。
 *
 * @author Liu Chunchi
 */
public interface MarkdownEngine {

    /**
     * 渲染 markdown 文本为 HTML 字符串（包含 {@code <html><body>} 外壳）。
     *
     * @param markdown 原始 markdown 文本，可为 {@code null}
     * @return 完整 HTML 文档字符串，至少包含基本视觉样式
     */
    String toHtml(String markdown);
}
