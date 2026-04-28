package io.github.movebrickschi.dailynewspapergenerator.ui;

/**
 * 极简 Markdown → HTML 渲染。
 * 仅覆盖 # ~ ###、列表、加粗 / 斜体 / 行内代码 / 代码块 / 段落 / 链接，足以让预览页基本可读。
 * 复杂场景请使用 IntelliJ Markdown 插件。
 *
 * @author Liu Chunchi
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    public static String toHtml(String markdown) {
        if (markdown == null) {
            return "<html><body></body></html>";
        }
        StringBuilder body = new StringBuilder();
        String[] lines = markdown.split("\\r?\\n", -1);

        boolean inCode = false;
        boolean inUl = false;
        StringBuilder paragraph = new StringBuilder();

        for (String raw : lines) {
            String line = raw;
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flushParagraph(body, paragraph);
                if (inUl) {
                    body.append("</ul>\n");
                    inUl = false;
                }
                if (inCode) {
                    body.append("</pre>\n");
                    inCode = false;
                } else {
                    body.append("<pre style=\"background:#2b2b2b;color:#a9b7c6;padding:8px;\">");
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
                if (inUl) {
                    body.append("</ul>\n");
                    inUl = false;
                }
                continue;
            }
            if (trimmed.startsWith("### ")) {
                flushParagraph(body, paragraph);
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                body.append("<h3>").append(inline(trimmed.substring(4))).append("</h3>\n");
                continue;
            }
            if (trimmed.startsWith("## ")) {
                flushParagraph(body, paragraph);
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                body.append("<h2>").append(inline(trimmed.substring(3))).append("</h2>\n");
                continue;
            }
            if (trimmed.startsWith("# ")) {
                flushParagraph(body, paragraph);
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                body.append("<h1>").append(inline(trimmed.substring(2))).append("</h1>\n");
                continue;
            }
            if (trimmed.equals("---") || trimmed.equals("***")) {
                flushParagraph(body, paragraph);
                if (inUl) { body.append("</ul>\n"); inUl = false; }
                body.append("<hr/>\n");
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                flushParagraph(body, paragraph);
                if (!inUl) { body.append("<ul>\n"); inUl = true; }
                body.append("<li>").append(inline(trimmed.substring(2))).append("</li>\n");
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        flushParagraph(body, paragraph);
        if (inUl) {
            body.append("</ul>\n");
        }
        if (inCode) {
            body.append("</pre>\n");
        }

        return "<html><body style=\"font-family: sans-serif; padding:8px;\">"
                + body
                + "</body></html>";
    }

    private static void flushParagraph(StringBuilder body, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        body.append("<p>").append(inline(paragraph.toString())).append("</p>\n");
        paragraph.setLength(0);
    }

    private static String inline(String s) {
        String e = escape(s);
        // 加粗 **x**
        e = e.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        // 斜体 *x*
        e = e.replaceAll("(?<!\\*)\\*(?!\\s)([^*]+?)(?<!\\s)\\*(?!\\*)", "<i>$1</i>");
        // 行内代码 `x`
        e = e.replaceAll("`([^`]+?)`",
                "<code style='background:#eee;padding:0 2px;'>$1</code>");
        // 链接 [text](url)
        e = e.replaceAll("\\[(.+?)\\]\\((https?://[^)]+)\\)", "<a href=\"$2\">$1</a>");
        return e;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
