package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 椭圆胶囊状徽章。
 *
 * <p>用于在列表项 / 详情头部展示「类型 / 状态」分类，背景色取自 {@link UiTokens.Colors}。
 * 文字颜色根据背景明度自动选择黑或白。</p>
 *
 * @author Liu Chunchi
 */
public class RoundedBadge extends JBLabel {

    private Color bg;

    public RoundedBadge(String text, JBColor background) {
        super(text);
        this.bg = background == null ? UiTokens.Colors.BADGE_GENERIC : background;
        setBorder(JBUI.Borders.empty(2, 8));
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.PLAIN, getFont().getSize2D() - 1f));
        setForeground(textForBackground(this.bg));
    }

    public void setBackgroundColor(JBColor color) {
        this.bg = color == null ? UiTokens.Colors.BADGE_GENERIC : color;
        setForeground(textForBackground(this.bg));
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, JBUI.scale(18));
        d.width = d.width + JBUI.scale(6);
        return d;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, h, h);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /**
     * 按 WCAG 2.1 contrast ratio 选黑/白文字，目标 ≥ 4.5:1（正文级别可读性）。
     * 黑白二选一时通常黑色对浅背景、白色对深背景。若两者对比度都不达标，
     * 则选取较高者并以 IDE 默认 label 前景兜底。
     */
    private static Color textForBackground(Color bg) {
        if (bg == null) {
            return UIUtil.getLabelForeground();
        }
        Color black = new Color(0x222222);
        Color white = new Color(0xF5F5F5);
        double ratioBlack = contrastRatio(bg, black);
        double ratioWhite = contrastRatio(bg, white);
        if (ratioBlack >= ratioWhite) {
            return ratioBlack >= 4.5 ? black : UIUtil.getLabelForeground();
        } else {
            return ratioWhite >= 4.5 ? white : UIUtil.getLabelForeground();
        }
    }

    /** WCAG 2.1 § 1.4.3 contrast ratio: (L_lighter + 0.05) / (L_darker + 0.05). */
    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** WCAG 2.1 relative luminance：sRGB → linear → 加权和。 */
    private static double relativeLuminance(Color c) {
        double r = channelLinear(c.getRed() / 255.0);
        double g = channelLinear(c.getGreen() / 255.0);
        double b = channelLinear(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelLinear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
