package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExtractOptions;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽取范围选项对话框。
 *
 * @author Liu Chunchi
 */
public class ExtractRangeDialog extends DialogWrapper {

    private final ComboBox<ExtractOptions.DateRange> rangeCombo = new ComboBox<>(new ExtractOptions.DateRange[]{
            ExtractOptions.DateRange.TODAY,
            ExtractOptions.DateRange.YESTERDAY,
            ExtractOptions.DateRange.THIS_WEEK,
            ExtractOptions.DateRange.LAST_WEEK,
            ExtractOptions.DateRange.LAST_7_DAYS,
            ExtractOptions.DateRange.CUSTOM
    });
    private final JBTextField sinceField = new JBTextField();
    private final JBTextField untilField = new JBTextField();
    private final JBTextField authorsField = new JBTextField();
    private final JBCheckBox skipMerges = new JBCheckBox("跳过 merge commit", true);
    private final JBCheckBox skipReverts = new JBCheckBox("跳过 revert commit", false);
    private final JBCheckBox skipWip = new JBCheckBox("跳过 WIP commit", false);
    private final JBCheckBox classify = new JBCheckBox("按 Conventional Commits 分组（feat/fix/...）", true);
    private final JBCheckBox stats = new JBCheckBox("末尾附加 commit 数 / 代码行变更统计", true);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ExtractRangeDialog(@Nullable Project project) {
        super(project);
        setTitle("提取提交记录");
        setOKButtonText("提取");
        init();

        sinceField.setColumns(10);
        untilField.setColumns(10);
        authorsField.getEmptyText().setText("逗号分隔，留空=自己");

        rangeCombo.addActionListener(e -> updateCustomEnabled());
        rangeCombo.setSelectedItem(ExtractOptions.DateRange.TODAY);
        sinceField.setText(LocalDate.now().format(ISO));
        untilField.setText(LocalDate.now().format(ISO));
        updateCustomEnabled();
    }

    private void updateCustomEnabled() {
        boolean custom = rangeCombo.getSelectedItem() == ExtractOptions.DateRange.CUSTOM;
        sinceField.setEnabled(custom);
        untilField.setEnabled(custom);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        rangeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ExtractOptions.DateRange r) {
                    setText(label(r));
                }
                return c;
            }
        });

        JPanel dateRow = new JPanel();
        dateRow.setLayout(new BoxLayout(dateRow, BoxLayout.X_AXIS));
        dateRow.add(sinceField);
        dateRow.add(Box.createHorizontalStrut(JBUI.scale(8)));
        JBLabel toLabel = new JBLabel("结束:");
        toLabel.setBorder(JBUI.Borders.emptyRight(6));
        dateRow.add(toLabel);
        dateRow.add(untilField);
        dateRow.add(Box.createHorizontalGlue());

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("时间范围:"), rangeCombo, 1, false)
                .addLabeledComponent(new JBLabel("起始日期:"), dateRow, 1, false)
                .addLabeledComponent(new JBLabel("作者:"), authorsField, 1, false)
                .addComponentToRightColumn(skipMerges)
                .addComponentToRightColumn(skipReverts)
                .addComponentToRightColumn(skipWip)
                .addComponentToRightColumn(classify)
                .addComponentToRightColumn(stats)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(10));
        panel.setPreferredSize(new Dimension(JBUI.scale(440), panel.getPreferredSize().height));
        return panel;
    }

    private static String label(ExtractOptions.DateRange r) {
        return switch (r) {
            case TODAY -> "今天";
            case YESTERDAY -> "昨天";
            case THIS_WEEK -> "本周（周一至今）";
            case LAST_WEEK -> "上周（周一到周日）";
            case LAST_7_DAYS -> "最近 7 天";
            case CUSTOM -> "自定义日期";
        };
    }

    /** 把 UI 选项打包为 {@link ExtractOptions}。如果自定义日期不合法返回 null。 */
    @Nullable
    public ExtractOptions toOptions() {
        ExtractOptions opt = new ExtractOptions();
        opt.range = (ExtractOptions.DateRange) rangeCombo.getSelectedItem();
        if (opt.range == ExtractOptions.DateRange.CUSTOM) {
            try {
                opt.customSince = LocalDate.parse(sinceField.getText().trim(), ISO);
                opt.customUntil = LocalDate.parse(untilField.getText().trim(), ISO);
            } catch (Exception e) {
                return null;
            }
        }
        opt.authors = parseAuthors(authorsField.getText());
        opt.skipMerges = skipMerges.isSelected();
        opt.skipReverts = skipReverts.isSelected();
        opt.skipWip = skipWip.isSelected();
        opt.classifyByConventional = classify.isSelected();
        opt.includeStats = stats.isSelected();
        return opt;
    }

    private static List<String> parseAuthors(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
