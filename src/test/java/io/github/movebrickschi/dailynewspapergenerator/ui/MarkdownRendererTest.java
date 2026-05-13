package io.github.movebrickschi.dailynewspapergenerator.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link MarkdownRenderer#toHtml(String)} 安全 / 行为单测。
 *
 * <p>重点验证安全约束：</p>
 * <ul>
 *   <li>HTML 元字符必须 escape，避免 XSS</li>
 *   <li>链接 href 仅放行 http/https/mailto/相对路径，{@code javascript:} 等被替换为 {@code #}</li>
 *   <li>常见结构（标题 / 列表 / 表格 / 代码块）能产生预期标签</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class MarkdownRendererTest {

    @Test
    public void nullInput_returnsValidHtmlShell() {
        String html = MarkdownRenderer.toHtml(null);
        assertNotNull(html);
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void htmlSpecialCharsAreEscaped() {
        String html = MarkdownRenderer.toHtml("plain <script>alert(1)</script> text");
        assertFalse("script tag should not be raw", html.contains("<script>"));
        assertTrue("escaped form expected", html.contains("&lt;script&gt;"));
    }

    @Test
    public void javascriptLinkScheme_rewrittenToHash() {
        String html = MarkdownRenderer.toHtml("[click me](javascript:alert(1))");
        assertFalse("javascript: scheme must not survive", html.contains("javascript:"));
        assertTrue("should fall back to hash href", html.contains("href=\"#\""));
    }

    @Test
    public void httpLinkAllowed() {
        String html = MarkdownRenderer.toHtml("[home](https://example.com)");
        assertTrue(html.contains("href=\"https://example.com\""));
        assertTrue(html.contains(">home</a>"));
    }

    @Test
    public void headingLevelsProduceHTags() {
        String html = MarkdownRenderer.toHtml("# h1\n## h2\n### h3");
        assertTrue(html.contains("<h1>h1</h1>"));
        assertTrue(html.contains("<h2>h2</h2>"));
        assertTrue(html.contains("<h3>h3</h3>"));
    }

    @Test
    public void unorderedListWraps() {
        String html = MarkdownRenderer.toHtml("- one\n- two\n- three");
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>one</li>"));
        assertTrue(html.contains("<li>two</li>"));
        assertTrue(html.contains("<li>three</li>"));
        assertTrue(html.contains("</ul>"));
    }

    @Test
    public void codeBlockKeepsLanguageAsIs() {
        String md = "```\nint x = 1;\n```";
        String html = MarkdownRenderer.toHtml(md);
        assertTrue(html.contains("<pre"));
        assertTrue(html.contains("int x = 1;"));
    }

    @Test
    public void gfmTableRenders() {
        String md = "| h1 | h2 |\n| --- | --- |\n| a | b |\n";
        String html = MarkdownRenderer.toHtml(md);
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("<th>h1</th>"));
        assertTrue(html.contains("<th>h2</th>"));
        assertTrue(html.contains("<td>a</td>"));
        assertTrue(html.contains("<td>b</td>"));
    }

    @Test
    public void boldAndInlineCode() {
        String html = MarkdownRenderer.toHtml("hello **world** and `code`");
        assertTrue(html.contains("<b>world</b>"));
        assertTrue(html.contains("<code>code</code>"));
    }
}
