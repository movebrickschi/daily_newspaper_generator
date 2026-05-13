package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.i18n.DailyReportBundle;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExtractOptions;
import io.github.movebrickschi.dailynewspapergenerator.utils.GitUserUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽取范围选项对话框。
 *
 * <p>升级点：</p>
 * <ul>
 *   <li>日期输入改为 {@link SimpleDatePickerField}，支持日历弹出选择</li>
 *   <li>作者输入改为 {@link AuthorAutoCompleteField} + 异步加载 git author 候选</li>
 *   <li>所有面板复选框带 tooltip 解释功能含义</li>
 *   <li>表单验证：通过 {@link #doValidate()} 拦截非法日期 / 起始晚于结束的错误</li>
 *   <li>记忆上次选择：通过 {@link PropertiesComponent} 持久化所有选项</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class ExtractRangeDialog extends DialogWrapper {

    private static final String PREF_PREFIX = "DailyReport.range.";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Nullable
    private final Project project;

    private final ComboBox<ExtractOptions.DateRange> rangeCombo = new ComboBox<>(new ExtractOptions.DateRange[]{
            ExtractOptions.DateRange.TODAY,
            ExtractOptions.DateRange.YESTERDAY,
            ExtractOptions.DateRange.THIS_WEEK,
            ExtractOptions.DateRange.LAST_WEEK,
            ExtractOptions.DateRange.LAST_7_DAYS,
            ExtractOptions.DateRange.CUSTOM
    });

    private final SimpleDatePickerField sinceField = new SimpleDatePickerField();
    private final SimpleDatePickerField untilField = new SimpleDatePickerField();
    private final AuthorAutoCompleteField authorsField = new AuthorAutoCompleteField();
    private final JBCheckBox skipMerges = new JBCheckBox(DailyReportBundle.message("range.dialog.skipMerges"), true);
    private final JBCheckBox skipReverts = new JBCheckBox(DailyReportBundle.message("range.dialog.skipReverts"), false);
    private final JBCheckBox skipWip = new JBCheckBox(DailyReportBundle.message("range.dialog.skipWip"), false);
    private final JBCheckBox classify = new JBCheckBox(DailyReportBundle.message("range.dialog.classify"), true);
    private final JBCheckBox stats = new JBCheckBox(DailyReportBundle.message("range.dialog.stats"), true);

    public ExtractRangeDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle(DailyReportBundle.message("range.dialog.title"));
        setOKButtonText(DailyReportBundle.message("range.dialog.button.extract"));
        setCancelButtonText(DailyReportBundle.message("range.dialog.button.cancel"));
        init();

        authorsField.getEmptyText().setText(DailyReportBundle.message("range.dialog.authors.label"));
        configureTooltips();
        applyPersistedSelection();
        rangeCombo.addActionListener(e -> updateCustomEnabled());
        updateCustomEnabled();
        if (project != null) {
            loadAuthorCandidatesAsync();
        }
    }

    private void configureTooltips() {
        rangeCombo.setToolTipText(DailyReportBundle.message("range.dialog.range.tooltip"));
        sinceField.getTextField().setToolTipText(DailyReportBundle.message("range.dialog.since.tooltip"));
        untilField.getTextField().setToolTipText(DailyReportBundle.message("range.dialog.until.tooltip"));
        authorsField.setToolTipText(DailyReportBundle.message("range.dialog.authors.tooltip"));
        skipMerges.setToolTipText(DailyReportBundle.message("range.dialog.skipMerges.tooltip"));
        skipReverts.setToolTipText(DailyReportBundle.message("range.dialog.skipReverts.tooltip"));
        skipWip.setToolTipText(DailyReportBundle.message("range.dialog.skipWip.tooltip"));
        classify.setToolTipText(DailyReportBundle.message("range.dialog.classify.tooltip"));
        stats.setToolTipText(DailyReportBundle.message("range.dialog.stats.tooltip"));
    }

    private void applyPersistedSelection() {
        PropertiesComponent prefs = PropertiesComponent.getInstance();
        String savedRange = prefs.getValue(PREF_PREFIX + "range");
        ExtractOptions.DateRange initial = ExtractOptions.DateRange.TODAY;
        if (savedRange != null) {
            try {
                initial = ExtractOptions.DateRange.valueOf(savedRange);
            } catch (IllegalArgumentException ignored) {
                initial = ExtractOptions.DateRange.TODAY;
            }
        }
        rangeCombo.setSelectedItem(initial);

        String since = prefs.getValue(PREF_PREFIX + "since");
        String until = prefs.getValue(PREF_PREFIX + "until");
        sinceField.setText(since == null || since.isBlank() ? LocalDate.now().format(ISO) : since);
        untilField.setText(until == null || until.isBlank() ? LocalDate.now().format(ISO) : until);

        String authors = prefs.getValue(PREF_PREFIX + "authors");
        if (authors != null) {
            authorsField.setText(authors);
        }
        skipMerges.setSelected(prefs.getBoolean(PREF_PREFIX + "skipMerges", true));
        skipReverts.setSelected(prefs.getBoolean(PREF_PREFIX + "skipReverts", false));
        skipWip.setSelected(prefs.getBoolean(PREF_PREFIX + "skipWip", false));
        classify.setSelected(prefs.getBoolean(PREF_PREFIX + "classify", true));
        stats.setSelected(prefs.getBoolean(PREF_PREFIX + "stats", true));
    }

    private void persistSelection() {
        PropertiesComponent prefs = PropertiesComponent.getInstance();
        ExtractOptions.DateRange r = (ExtractOptions.DateRange) rangeCombo.getSelectedItem();
        if (r != null) {
            prefs.setValue(PREF_PREFIX + "range", r.name());
        }
        prefs.setValue(PREF_PREFIX + "since", sinceField.getText());
        prefs.setValue(PREF_PREFIX + "until", untilField.getText());
        prefs.setValue(PREF_PREFIX + "authors", authorsField.getText() == null ? "" : authorsField.getText());
        prefs.setValue(PREF_PREFIX + "skipMerges", skipMerges.isSelected(), true);
        prefs.setValue(PREF_PREFIX + "skipReverts", skipReverts.isSelected(), false);
        prefs.setValue(PREF_PREFIX + "skipWip", skipWip.isSelected(), false);
        prefs.setValue(PREF_PREFIX + "classify", classify.isSelected(), true);
        prefs.setValue(PREF_PREFIX + "stats", stats.isSelected(), true);
    }

    private void loadAuthorCandidatesAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<String> authors;
            try {
                authors = GitUserUtil.listKnownAuthors(project, 200);
            } catch (Exception e) {
                authors = List.of();
            }
            List<String> finalAuthors = authors;
            ApplicationManager.getApplication().invokeLater(() ->
                    authorsField.setCandidates(finalAuthors));
        });
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
        JBLabel toLabel = new JBLabel(DailyReportBundle.message("range.dialog.until.label") + ":");
        toLabel.setBorder(JBUI.Borders.emptyRight(6));
        dateRow.add(toLabel);
        dateRow.add(untilField);
        dateRow.add(Box.createHorizontalGlue());
        // 当 rangeCombo != CUSTOM 时整个区域 disable, 加 IdeBorderFactory.createTitledBorder 视觉提示该
        // 区域只对 "自定义" 生效, 避免用户在选其他范围时困惑为何输入框是灰的。
        dateRow.setBorder(com.intellij.ui.IdeBorderFactory.createTitledBorder(
                DailyReportBundle.message("range.dialog.custom.section"), false));

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel(DailyReportBundle.message("range.dialog.range.label") + ":"),
                        rangeCombo, 1, false)
                .addLabeledComponent(new JBLabel(DailyReportBundle.message("range.dialog.since.label") + ":"),
                        dateRow, 1, false)
                .addLabeledComponent(new JBLabel(DailyReportBundle.message("range.dialog.authors.label") + ":"),
                        authorsField, 1, false)
                .addComponentToRightColumn(skipMerges)
                .addComponentToRightColumn(skipReverts)
                .addComponentToRightColumn(skipWip)
                .addComponentToRightColumn(classify)
                .addComponentToRightColumn(stats)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(10));
        panel.setPreferredSize(new Dimension(JBUI.scale(480), panel.getPreferredSize().height));
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (rangeCombo.getSelectedItem() != ExtractOptions.DateRange.CUSTOM) {
            return null;
        }
        LocalDate since = parseSafely(sinceField.getText());
        LocalDate until = parseSafely(untilField.getText());
        if (since == null) {
            return new ValidationInfo(DailyReportBundle.message("range.dialog.error.invalid_date"),
                    sinceField.getTextField());
        }
        if (until == null) {
            return new ValidationInfo(DailyReportBundle.message("range.dialog.error.invalid_date"),
                    untilField.getTextField());
        }
        if (since.isAfter(until)) {
            return new ValidationInfo(DailyReportBundle.message("range.dialog.error.since_after_until"),
                    sinceField.getTextField());
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        persistSelection();
        super.doOKAction();
    }

    @Nullable
    private static LocalDate parseSafely(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String label(ExtractOptions.DateRange r) {
        return switch (r) {
            case TODAY -> DailyReportBundle.message("range.dialog.range.today");
            case YESTERDAY -> DailyReportBundle.message("range.dialog.range.yesterday");
            case THIS_WEEK -> DailyReportBundle.message("range.dialog.range.thisWeek");
            case LAST_WEEK -> DailyReportBundle.message("range.dialog.range.lastWeek");
            case LAST_7_DAYS -> DailyReportBundle.message("range.dialog.range.last7");
            case CUSTOM -> DailyReportBundle.message("range.dialog.range.custom");
        };
    }

    /** 把 UI 选项打包为 {@link ExtractOptions}。如果自定义日期不合法返回 null。 */
    @Nullable
    public ExtractOptions toOptions() {
        ExtractOptions opt = new ExtractOptions();
        opt.range = (ExtractOptions.DateRange) rangeCombo.getSelectedItem();
        if (opt.range == ExtractOptions.DateRange.CUSTOM) {
            LocalDate since = parseSafely(sinceField.getText());
            LocalDate until = parseSafely(untilField.getText());
            if (since == null || until == null || since.isAfter(until)) {
                return null;
            }
            opt.customSince = since;
            opt.customUntil = until;
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
