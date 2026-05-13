package io.github.movebrickschi.dailynewspapergenerator.ui;

import javax.swing.Icon;
import javax.swing.JButton;

/**
 * {@link ReportDialog} 底部三档按钮（mainAction / primary / secondary）的统一工厂。
 *
 * <p>从 dialog 中外提以保持 UI 风格一致：mainAction 走 IDE 默认按钮强调色；
 * secondary 使用 {@code borderless} 风格以弱化视觉权重；primary 介于二者之间。
 *
 * @author Liu Chunchi
 */
final class ReportButtonFactory {

    private ReportButtonFactory() {
    }

    /** 主操作按钮（如「推送」）：使用 IDE 默认按钮强调色 + gotoAction 提示。 */
    static JButton mainAction(String text, Icon icon, String tooltip) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tooltip);
        b.putClientProperty("JButton.buttonType", "default");
        b.putClientProperty("gotoAction", Boolean.TRUE);
        return b;
    }

    /** 常规按钮（如「复制」）：无特殊视觉处理。 */
    static JButton primary(String text, Icon icon, String tooltip) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tooltip);
        return b;
    }

    /** 次要按钮（如「关闭」「再润色」「导出」）：borderless 风格，弱化视觉权重。 */
    static JButton secondary(String text, Icon icon, String tooltip) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tooltip);
        b.putClientProperty("JButton.buttonType", "borderless");
        return b;
    }
}
