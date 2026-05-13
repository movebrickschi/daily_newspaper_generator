package io.github.movebrickschi.dailynewspapergenerator.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 同一份内容同时以 {@code text/plain}（原始 markdown）和 {@code text/html} 暴露给剪贴板。
 *
 * <p>这样在「钉钉 / 企微 / 飞书」等内置 markdown 但富文本渲染不强的客户端可以粘贴
 * markdown 原文，而在 Word / Outlook / 浏览器编辑器中粘贴时则得到富文本 HTML。</p>
 *
 * <p>实现遵循 AWT {@link Transferable} 规范，仅支持 string / html / inputStream 三种 flavor，
 * 其它请求会抛 {@link UnsupportedFlavorException}。</p>
 *
 * @author Liu Chunchi
 */
final class MarkdownHtmlTransferable implements Transferable {

    private static final DataFlavor[] FLAVORS = new DataFlavor[]{
            DataFlavor.stringFlavor,
            DataFlavor.allHtmlFlavor,
            DataFlavor.fragmentHtmlFlavor,
            DataFlavor.selectionHtmlFlavor
    };

    private final String markdown;
    private final String html;

    MarkdownHtmlTransferable(@NotNull String markdown, @NotNull String html) {
        this.markdown = markdown;
        this.html = html;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor == null) return false;
        for (DataFlavor f : FLAVORS) {
            if (f.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return markdown;
        }
        if (DataFlavor.allHtmlFlavor.equals(flavor)
                || DataFlavor.fragmentHtmlFlavor.equals(flavor)
                || DataFlavor.selectionHtmlFlavor.equals(flavor)) {
            // 这几个 flavor 的 representationClass 都是 Reader / String / InputStream，
            // 简化为 String 处理；AWT 的 SystemFlavorMap 会按需做转换。
            if (flavor.getRepresentationClass() == InputStream.class) {
                return new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
            }
            if (flavor.getRepresentationClass() == java.io.Reader.class) {
                return new java.io.StringReader(html);
            }
            return html;
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
