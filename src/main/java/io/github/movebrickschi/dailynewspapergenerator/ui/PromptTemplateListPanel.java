package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.config.PromptTemplate;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词模板列表编辑面板：左侧列表 + 右侧详情。
 * 同时管理"激活模板 id"。
 *
 * @author Liu Chunchi
 */
public class PromptTemplateListPanel {

    /** 内置预设：作为「新增预设」的下拉项。 */
    private static final Map<String, String> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("日报",
                "请将以下Git提交信息润色成更专业的日报格式，按「今日完成」「明日计划」「问题/风险」三段输出，保持简洁明了，使用 Markdown。");
        PRESETS.put("周报",
                "请将以下一周内的Git提交信息汇总为周报，按「核心进展」「主要功能」「质量改进」「下周计划」分组，每条 1-2 句，使用 Markdown。");
        PRESETS.put("月报",
                "请把以下一个月的Git提交信息汇总成月度工作报告，提炼成果与亮点，按业务主题归类，使用 Markdown 标题。");
        PRESETS.put("复盘",
                "请基于以下Git提交信息撰写复盘报告，包含「目标回顾」「实际产出」「关键决策」「待改进」四部分。");
        PRESETS.put("English Daily",
                "Polish the following git commit messages into a concise English daily report. Use Markdown with sections: Done Today, Plan Tomorrow, Risks/Blockers.");
    }

    private final DefaultListModel<PromptTemplate> listModel = new DefaultListModel<>();
    private final JBList<PromptTemplate> list = new JBList<>(listModel);
    private final JBTextField nameField = new JBTextField();
    private final JTextArea contentArea = new JTextArea(10, 50);
    private final JCheckBox activeCheck = new JCheckBox("设为当前激活模板");

    private final JPanel root;

    private String activeId = "";
    private PromptTemplate currentEditing;
    private boolean suppressUpdate = false;

    public PromptTemplateListPanel() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof PromptTemplate t) {
                    String label = t.name == null || t.name.isBlank() ? "(未命名)" : t.name;
                    if (activeId != null && activeId.equals(t.id)) {
                        label = "★ " + label;
                    }
                    setText(label);
                }
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            loadDetail(list.getSelectedValue());
        });

        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);

        nameField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        contentArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        activeCheck.addActionListener(e -> {
            if (currentEditing != null) {
                if (activeCheck.isSelected()) {
                    activeId = currentEditing.id;
                } else if (currentEditing.id.equals(activeId)) {
                    activeId = "";
                }
                list.repaint();
            }
        });

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(list)
                .setAddAction(button -> showAddMenu(button))
                .setRemoveAction(button -> {
                    int idx = list.getSelectedIndex();
                    if (idx < 0) {
                        return;
                    }
                    PromptTemplate removed = listModel.remove(idx);
                    if (removed != null && removed.id.equals(activeId)) {
                        activeId = "";
                    }
                    int next = Math.min(idx, listModel.size() - 1);
                    if (next >= 0) {
                        list.setSelectedIndex(next);
                    } else {
                        loadDetail(null);
                    }
                });

        JPanel detailPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("名称:"), nameField, 1, false)
                .addLabeledComponent(new JBLabel("内容:"), new JBScrollPane(contentArea), 1, true)
                .addComponent(activeCheck)
                .getPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(decorator.createPanel());
        split.setRightComponent(detailPanel);
        split.setDividerLocation(JBUI.scale(220));
        split.setBorder(JBUI.Borders.empty(8));

        root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);

        loadDetail(null);
    }

    private void showAddMenu(AnActionButton button) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem blank = new JMenuItem("新建空模板");
        blank.addActionListener(e -> addTemplate(new PromptTemplate("新模板", "")));
        menu.add(blank);
        menu.addSeparator();
        for (Map.Entry<String, String> entry : PRESETS.entrySet()) {
            JMenuItem mi = new JMenuItem("预设: " + entry.getKey());
            mi.addActionListener(e -> addTemplate(new PromptTemplate(entry.getKey(), entry.getValue())));
            menu.add(mi);
        }
        menu.show(button.getContextComponent(), button.getContextComponent().getWidth() / 2, 0);
    }

    private void addTemplate(PromptTemplate t) {
        listModel.addElement(t);
        list.setSelectedValue(t, true);
    }

    private void loadDetail(PromptTemplate t) {
        suppressUpdate = true;
        try {
            currentEditing = t;
            if (t == null) {
                nameField.setText("");
                contentArea.setText("");
                activeCheck.setSelected(false);
                nameField.setEnabled(false);
                contentArea.setEnabled(false);
                activeCheck.setEnabled(false);
            } else {
                nameField.setText(t.name == null ? "" : t.name);
                contentArea.setText(t.content == null ? "" : t.content);
                activeCheck.setSelected(t.id.equals(activeId));
                nameField.setEnabled(true);
                contentArea.setEnabled(true);
                activeCheck.setEnabled(true);
            }
        } finally {
            suppressUpdate = false;
        }
    }

    private void saveDetail() {
        if (suppressUpdate || currentEditing == null) {
            return;
        }
        currentEditing.name = nameField.getText();
        currentEditing.content = contentArea.getText();
        list.repaint();
    }

    public JComponent getRoot() {
        return root;
    }

    public List<PromptTemplate> getData() {
        List<PromptTemplate> out = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            out.add(listModel.get(i));
        }
        return out;
    }

    public String getActiveId() {
        return activeId == null ? "" : activeId;
    }

    public void setData(List<PromptTemplate> data, String activeId) {
        listModel.clear();
        if (data != null) {
            for (PromptTemplate t : data) {
                if (t != null) {
                    listModel.addElement(t);
                }
            }
        }
        this.activeId = activeId == null ? "" : activeId;
        if (listModel.size() > 0) {
            list.setSelectedIndex(0);
        } else {
            loadDetail(null);
        }
    }

    /** 简单 DocumentListener 适配。 */
    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }

    @SuppressWarnings("unused")
    private static void noop(Object o) {
        // 占位，保留 Messages 引用避免被 import 优化掉（后续可能需要）
        if (o == null) {
            Messages.getInformationIcon();
        }
    }
}
