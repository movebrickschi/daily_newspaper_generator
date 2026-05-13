package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.ui.JBColor;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 Markdown → HTML 渲染。
 * <p>覆盖 h1~h6 / 有序列表 / 无序列表 / 加粗 / 斜体 / 行内代码 / 代码块 /
 * 引用块 / 段落 / 链接 / 分隔线 / GFM 表格，足以让预览页基本可读。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>所有渲染节点先 HTML escape，避免 XSS</li>
 *   <li>链接 href 仅允许 http/https/mailto，其它 scheme（javascript: / file: / data:）被替换为 #</li>
 * </ul>
 *
 * <p>视觉规范：</p>
 * <ul>
 *   <li>样式抽到 wrap() 的 &lt;style&gt; 头部，自适应深/浅主题</li>
 *   <li>代码字体为系统等宽，背景为 IDE 配色风格</li>
 *   <li>段落行距与字号匹配 IntelliJ 默认 UI 字号</li>
 * </ul>
 *
 * <p>复杂场景请使用 IntelliJ Markdown 插件 API。</p>
 *
 * @author Liu Chunchi
 */
public final class MarkdownRenderer {

    /** 允许出现在 `<a href>` 的 scheme 前缀。 */
    private static final Pattern SAFE_URL = Pattern.compile("^(https?|mailto):", Pattern.CASE_INSENSITIVE);
    private static final Pattern OL_LINE = Pattern.compile("^\\d+\\.\\s+(.*)$");
    private static final Pattern TABLE_DELIMITER = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\[\\]]+?)\\]\\(([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\)");

    private MarkdownRenderer() {
    }

    public static String toHtml(String markdown) {
        if (markdown == null) {
            return wrap("");
        }
        StringBuilder body = new StringBuilder();
        String[] lines = markdown.split("\\r?\\n", -1);

        boolean inCode = false;
        boolean inUl = false;
        boolean inOl = false;
        boolean inBlockquote = false;
        StringBuilder paragraph = new StringBuilder();

        for (int idx = 0; idx < lines.length; idx++) {
            String raw = lines[idx];
            String line = raw;
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                if (inBlockquote) {
                    body.append("</blockquote>\n");
                    inBlockquote = false;
                }
                if (inCode) {
                    body.append("</code></pre>\n");
                    inCode = false;
                } else {
                    body.append("<pre class=\"code-block\"><code>");
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                body.append(escape(line)).append('\n');
                continue;
            }
            if (trimmed.isEmpty()) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                if (inBlockquote) {
                    body.append("</blockquote>\n");
                    inBlockquote = false;
                }
                continue;
            }

            int hLevel = headingLevel(trimmed);
            if (hLevel > 0) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                String text = trimmed.substring(hLevel + 1);
                body.append("<h").append(hLevel).append(">")
                        .append(inline(text))
                        .append("</h").append(hLevel).append(">\n");
                continue;
            }

            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                body.append("<hr/>\n");
                continue;
            }

            if (trimmed.startsWith("> ")) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                if (!inBlockquote) {
                    body.append("<blockquote>\n");
                    inBlockquote = true;
                }
                body.append("<p>").append(inline(trimmed.substring(2))).append("</p>\n");
                continue;
            } else if (inBlockquote) {
                body.append("</blockquote>\n");
                inBlockquote = false;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                flushParagraph(body, paragraph);
                if (inOl) { body.append("</ol>\n"); inOl = false; }
                if (!inUl) { body.append("<ul>\n"); inUl = true; }
                body.append("<li>").append(inline(trimmed.substring(2))).append("</li>\n");
                continue;
            }

            Matcher olm = OL_LINE.matcher(trimmed);
            if (olm.matches()) {
                flushParagraph(body, paragraph);
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                if (!inOl) { body.append("<ol>\n"); inOl = true; }
                body.append("<li>").append(inline(olm.group(1))).append("</li>\n");
                continue;
            }

            if (isTableHeader(idx, lines)) {
                flushParagraph(body, paragraph);
                closeLists(body, inUl, inOl);
                inUl = inOl = false;
                int consumed = renderTable(body, idx, lines);
                idx += consumed - 1;
                continue;
            }

            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        flushParagraph(body, paragraph);
        closeLists(body, inUl, inOl);
        if (inBlockquote) body.append("</blockquote>\n");
        if (inCode) body.append("</code></pre>\n");

        return wrap(body.toString());
    }

    private static boolean isTableHeader(int idx, String[] lines) {
        if (idx + 1 >= lines.length) return false;
        String h = lines[idx];
        String sep = lines[idx + 1];
        if (!h.contains("|")) return false;
        return TABLE_DELIMITER.matcher(sep).matches();
    }

    /** 渲染连续的表格行；返回消耗了多少行。 */
    private static int renderTable(StringBuilder body, int start, String[] lines) {
        String[] headers = splitTableRow(lines[start]);
        body.append("<table class=\"md-table\">\n<thead><tr>");
        for (String h : headers) {
            body.append("<th>").append(inline(h.trim())).append("</th>");
        }
        body.append("</tr></thead>\n<tbody>\n");
        int consumed = 2;
        for (int i = start + 2; i < lines.length; i++) {
            String row = lines[i];
            if (row.trim().isEmpty() || !row.contains("|")) {
                break;
            }
            String[] cells = splitTableRow(row);
            body.append("<tr>");
            for (int c = 0; c < headers.length; c++) {
                String cell = c < cells.length ? cells[c].trim() : "";
                body.append("<td>").append(inline(cell)).append("</td>");
            }
            body.append("</tr>\n");
            consumed++;
        }
        body.append("</tbody></table>\n");
        return consumed;
    }

    private static String[] splitTableRow(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed.split("\\s*\\|\\s*", -1);
    }

    private static int headingLevel(String trimmed) {
        int level = 0;
        while (level < 6 && level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level == 0) return 0;
        if (level >= trimmed.length() || trimmed.charAt(level) != ' ') return 0;
        return level;
    }

    private static void closeLists(StringBuilder body, boolean ul, boolean ol) {
        if (ul) body.append("</ul>\n");
        if (ol) body.append("</ol>\n");
    }

    private static void flushParagraph(StringBuilder body, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        body.append("<p>").append(inline(paragraph.toString())).append("</p>\n");
        paragraph.setLength(0);
    }

    private static String inline(String s) {
        String e = escape(s);
        e = e.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        e = e.replaceAll("(?<!\\*)\\*(?!\\s)([^*]+?)(?<!\\s)\\*(?!\\*)", "<i>$1</i>");
        e = e.replaceAll("`([^`]+?)`", "<code>$1</code>");
        Matcher m = LINK_PATTERN.matcher(e);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String text = m.group(1);
            String href = sanitizeUrl(m.group(2));
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<a href=\"" + href + "\">" + text + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return "#";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "#";
        if (SAFE_URL.matcher(trimmed).find()) {
            return escape(trimmed);
        }
        if (trimmed.startsWith("/") || trimmed.startsWith("#")) {
            return escape(trimmed);
        }
        return "#";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** 包一层 html / body，根据 IDE 当前主题（亮/暗）应用主题色。 */
    private static String wrap(String inner) {
        boolean dark = !JBColor.isBright();
        // 优先从 IDE Editor 配色方案中取前景 / 背景 / 字体，让预览跟随用户在
        // Settings -> Editor -> Color Scheme / Font 中的选择，不再用硬编码 sans-serif/13px。
        com.intellij.openapi.editor.colors.EditorColorsScheme scheme = null;
        try {
            scheme = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().getGlobalScheme();
        } catch (Throwable ignored) {
            // 测试 / 无 application 环境下回退到硬编码值
        }

        Color fg = scheme != null && scheme.getDefaultForeground() != null
                ? scheme.getDefaultForeground()
                : (dark ? new Color(0xCCCCCC) : new Color(0x222222));
        Color codeBg = dark ? new Color(0x2B2B2B) : new Color(0xF5F5F5);
        Color codeFg = dark ? new Color(0xA9B7C6) : new Color(0x333333);
        Color inlineCodeBg = dark ? new Color(0x3C3F41) : new Color(0xEEEEEE);
        Color quoteBorder = dark ? new Color(0x555555) : new Color(0xCCCCCC);
        Color quoteFg = dark ? new Color(0x999999) : new Color(0x666666);
        Color tableBorder = dark ? new Color(0x4B4B4B) : new Color(0xDADCE0);
        Color tableHeadBg = dark ? new Color(0x363839) : new Color(0xF1F3F5);

        String fontFamily;
        int fontSize;
        if (scheme != null) {
            fontFamily = scheme.getEditorFontName() != null
                    ? "'" + scheme.getEditorFontName().replace("'", "") + "', sans-serif"
                    : "sans-serif";
            // Editor font size 通常 12~14，预览正文用它会保持一致感
            fontSize = Math.max(11, Math.min(18, scheme.getEditorFontSize()));
        } else {
            fontFamily = "sans-serif";
            fontSize = 13;
        }

        String style = "body{font-family:" + fontFamily + ";font-size:" + fontSize
                + "px;line-height:1.55;padding:10px 12px;color:"
                + hex(fg) + ";}"
                + "h1,h2,h3,h4,h5,h6{margin:14px 0 8px;line-height:1.3;}"
                + "h1{font-size:1.6em;} h2{font-size:1.35em;} h3{font-size:1.18em;} h4,h5,h6{font-size:1.05em;}"
                + "p{margin:6px 0;}"
                + "ul,ol{margin:6px 0 6px 22px;padding:0;}"
                + "li{margin:2px 0;}"
                + "hr{border:none;border-top:1px solid " + hex(tableBorder) + ";margin:12px 0;}"
                + "a{color:" + (dark ? "#589DF6" : "#0969DA") + ";text-decoration:none;}"
                + "a:hover{text-decoration:underline;}"
                + "code{background:" + hex(inlineCodeBg) + ";padding:1px 5px;border-radius:3px;font-family:monospace;font-size:12px;}"
                + "pre.code-block{background:" + hex(codeBg) + ";color:" + hex(codeFg)
                + ";padding:10px 12px;border-radius:4px;font-family:monospace;font-size:12px;line-height:1.45;white-space:pre;margin:8px 0;}"
                + "pre.code-block code{background:transparent;padding:0;font-size:inherit;}"
                + "blockquote{border-left:3px solid " + hex(quoteBorder) + ";margin:8px 0;padding:4px 12px;color:" + hex(quoteFg) + ";}"
                + "table.md-table{border-collapse:collapse;margin:10px 0;font-size:12px;}"
                + "table.md-table th,table.md-table td{border:1px solid " + hex(tableBorder) + ";padding:4px 10px;text-align:left;}"
                + "table.md-table thead{background:" + hex(tableHeadBg) + ";}";
        return "<html><head><style>" + style + "</style></head><body>" + inner + "</body></html>";
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
