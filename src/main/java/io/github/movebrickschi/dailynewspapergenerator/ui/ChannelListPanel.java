package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig.ChannelType;
import io.github.movebrickschi.dailynewspapergenerator.config.SecureKeyStore;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推送通道列表编辑面板：左侧通道列表 + 右侧根据 type 渲染不同表单字段。
 *
 * @author Liu Chunchi
 */
public class ChannelListPanel {

    private final DefaultListModel<ChannelConfig> listModel = new DefaultListModel<>();
    private final JBList<ChannelConfig> list = new JBList<>(listModel);

    private final JBTextField nameField = new JBTextField();
    private final ComboBox<ChannelType> typeCombo = new ComboBox<>(ChannelType.values());

    private final JBTextField webhookUrlField = new JBTextField();
    private final JBPasswordField secretField = new JBPasswordField();
    private final JBTextField titleField = new JBTextField();
    private final JTextArea genericTemplateArea = new JTextArea(6, 50);

    private final JBTextField dingAppKeyField = new JBTextField();
    private final JBPasswordField dingAppSecretField = new JBPasswordField();
    private final JBTextField dingTemplateIdField = new JBTextField();
    private final JBTextField dingMainFieldField = new JBTextField();
    private final JBTextField dingToUserIdsField = new JBTextField();
    private final JBCheckBox dingToChatCheck = new JBCheckBox("同步推送到群");

    private final JPanel detailPanel = new JPanel(new CardLayout());

    private final JPanel root;
    private ChannelConfig current;
    private boolean suppress = false;
    private final Map<String, String> secretDrafts = new LinkedHashMap<>();
    private final Set<String> deletedSecretKeys = new LinkedHashSet<>();

    private static final String CARD_ROBOT = "robot";
    private static final String CARD_GENERIC = "generic";
    private static final String CARD_REPORT = "report";
    private static final String CARD_EMPTY = "empty";

    public ChannelListPanel() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value instanceof ChannelConfig ch) {
                    String name = ch.name == null || ch.name.isBlank() ? "(未命名)" : ch.name;
                    setText(name + "  [" + label(ch.type) + "]");
                }
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            loadDetail(list.getSelectedValue());
        });

        genericTemplateArea.setLineWrap(true);
        genericTemplateArea.setWrapStyleWord(true);

        nameField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        webhookUrlField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        secretField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveSecret));
        titleField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        genericTemplateArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        dingAppKeyField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        dingAppSecretField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveSecret));
        dingTemplateIdField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        dingMainFieldField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        dingToUserIdsField.getDocument().addDocumentListener(new SimpleDocumentListener(this::saveDetail));
        dingToChatCheck.addActionListener(e -> saveDetail());

        typeCombo.addActionListener(e -> {
            if (suppress || current == null) return;
            current.type = (ChannelType) typeCombo.getSelectedItem();
            switchCard();
            list.repaint();
        });

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(list)
                .setAddAction(button -> showAddMenu(button))
                .setRemoveAction(button -> {
                    int idx = list.getSelectedIndex();
                    if (idx < 0) return;
                    ChannelConfig removed = listModel.remove(idx);
                    if (removed != null) {
                        String key = removed.secretKey();
                        secretDrafts.remove(key);
                        deletedSecretKeys.add(key);
                    }
                    int next = Math.min(idx, listModel.size() - 1);
                    if (next >= 0) {
                        list.setSelectedIndex(next);
                    } else {
                        loadDetail(null);
                    }
                });

        detailPanel.add(buildEmpty(), CARD_EMPTY);
        detailPanel.add(buildRobotCard(), CARD_ROBOT);
        detailPanel.add(buildGenericCard(), CARD_GENERIC);
        detailPanel.add(buildReportCard(), CARD_REPORT);

        JPanel header = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("名称:"), nameField, 1, false)
                .addLabeledComponent(new JBLabel("类型:"), typeCombo, 1, false)
                .getPanel();
        header.setBorder(JBUI.Borders.emptyBottom(8));

        JPanel right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(detailPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(decorator.createPanel());
        split.setRightComponent(right);
        split.setDividerLocation(JBUI.scale(220));
        split.setBorder(JBUI.Borders.empty(8));

        root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        loadDetail(null);
    }

    private JComponent buildEmpty() {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JBLabel("<html><center>左侧选择或新增一个推送通道</center></html>"));
        return p;
    }

    private JComponent buildRobotCard() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Webhook URL:"), webhookUrlField, 1, false)
                .addLabeledComponent(new JBLabel("加签 Secret:"), secretField, 1, false)
                .addLabeledComponent(new JBLabel("标题:"), titleField, 1, false)
                .addComponent(new JBLabel("<html><i>飞书 Secret 仅用于加签校验；钉钉 Secret 为「加签」选项；企微留空。</i></html>"))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private JComponent buildGenericCard() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Webhook URL:"), webhookUrlField, 1, false)
                .addLabeledComponent(new JBLabel("Header Token:"), secretField, 1, false)
                .addLabeledComponent(new JBLabel("标题:"), titleField, 1, false)
                .addLabeledComponent(new JBLabel("请求体模板:"), new JBScrollPane(genericTemplateArea), 1, true)
                .addComponent(new JBLabel("<html><i>支持占位符 <b>{{title}}</b> <b>{{content}}</b>，需为合法 JSON。</i></html>"))
                .getPanel();
    }

    private JComponent buildReportCard() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("AppKey:"), dingAppKeyField, 1, false)
                .addLabeledComponent(new JBLabel("AppSecret:"), dingAppSecretField, 1, false)
                .addLabeledComponent(new JBLabel("Template ID:"), dingTemplateIdField, 1, false)
                .addLabeledComponent(new JBLabel("主字段名:"), dingMainFieldField, 1, false)
                .addLabeledComponent(new JBLabel("接收人 userids:"), dingToUserIdsField, 1, false)
                .addComponent(dingToChatCheck)
                .addComponent(new JBLabel("<html><i>主字段名必须与钉钉后台模板字段完全一致；接收人多个 userid 用英文逗号分隔。</i></html>"))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private void showAddMenu(com.intellij.ui.AnActionButton button) {
        JPopupMenu menu = new JPopupMenu();
        for (ChannelType t : ChannelType.values()) {
            JMenuItem mi = new JMenuItem("新增 " + label(t));
            mi.addActionListener(e -> addChannel(new ChannelConfig(t, label(t))));
            menu.add(mi);
        }
        menu.show(button.getContextComponent(), button.getContextComponent().getWidth() / 2, 0);
    }

    private static String label(ChannelType t) {
        if (t == null) return "?";
        return switch (t) {
            case FEISHU_ROBOT -> "飞书机器人";
            case DINGTALK_ROBOT -> "钉钉群机器人";
            case DINGTALK_REPORT -> "钉钉日志模板";
            case WECOM_ROBOT -> "企业微信机器人";
            case GENERIC_WEBHOOK -> "通用 Webhook";
        };
    }

    private void addChannel(ChannelConfig ch) {
        listModel.addElement(ch);
        list.setSelectedValue(ch, true);
    }

    private void switchCard() {
        CardLayout cl = (CardLayout) detailPanel.getLayout();
        if (current == null) {
            cl.show(detailPanel, CARD_EMPTY);
            return;
        }
        switch (current.type) {
            case GENERIC_WEBHOOK -> cl.show(detailPanel, CARD_GENERIC);
            case DINGTALK_REPORT -> cl.show(detailPanel, CARD_REPORT);
            default -> cl.show(detailPanel, CARD_ROBOT);
        }
    }

    private void loadDetail(ChannelConfig ch) {
        suppress = true;
        try {
            current = ch;
            if (ch == null) {
                nameField.setText("");
                typeCombo.setSelectedItem(ChannelType.FEISHU_ROBOT);
                webhookUrlField.setText("");
                secretField.setText("");
                titleField.setText("");
                genericTemplateArea.setText("");
                dingAppKeyField.setText("");
                dingAppSecretField.setText("");
                dingTemplateIdField.setText("");
                dingMainFieldField.setText("");
                dingToUserIdsField.setText("");
                dingToChatCheck.setSelected(false);
                setEditableAll(false);
                CardLayout cl = (CardLayout) detailPanel.getLayout();
                cl.show(detailPanel, CARD_EMPTY);
            } else {
                nameField.setText(ch.name == null ? "" : ch.name);
                typeCombo.setSelectedItem(ch.type == null ? ChannelType.FEISHU_ROBOT : ch.type);
                webhookUrlField.setText(ch.webhookUrl == null ? "" : ch.webhookUrl);
                titleField.setText(ch.title == null ? "" : ch.title);
                genericTemplateArea.setText(ch.genericTemplate == null ? "" : ch.genericTemplate);
                String key = ch.secretKey();
                String existingSecret = secretDrafts.containsKey(key)
                        ? secretDrafts.get(key)
                        : SecureKeyStore.load(key);
                secretField.setText(existingSecret);
                dingAppKeyField.setText(ch.dingAppKey == null ? "" : ch.dingAppKey);
                dingAppSecretField.setText(existingSecret);
                dingTemplateIdField.setText(ch.dingTemplateId == null ? "" : ch.dingTemplateId);
                dingMainFieldField.setText(ch.dingMainField == null ? "" : ch.dingMainField);
                dingToUserIdsField.setText(ch.dingToUserIds == null ? "" : ch.dingToUserIds);
                dingToChatCheck.setSelected(ch.dingToChat);
                setEditableAll(true);
                switchCard();
            }
        } finally {
            suppress = false;
        }
    }

    private void setEditableAll(boolean enabled) {
        nameField.setEnabled(enabled);
        typeCombo.setEnabled(enabled);
        webhookUrlField.setEnabled(enabled);
        secretField.setEnabled(enabled);
        titleField.setEnabled(enabled);
        genericTemplateArea.setEnabled(enabled);
        dingAppKeyField.setEnabled(enabled);
        dingAppSecretField.setEnabled(enabled);
        dingTemplateIdField.setEnabled(enabled);
        dingMainFieldField.setEnabled(enabled);
        dingToUserIdsField.setEnabled(enabled);
        dingToChatCheck.setEnabled(enabled);
    }

    private void saveDetail() {
        if (suppress || current == null) {
            return;
        }
        current.name = nameField.getText();
        current.webhookUrl = webhookUrlField.getText();
        current.title = titleField.getText();
        current.genericTemplate = genericTemplateArea.getText();
        current.dingAppKey = dingAppKeyField.getText();
        current.dingTemplateId = dingTemplateIdField.getText();
        current.dingMainField = dingMainFieldField.getText();
        current.dingToUserIds = dingToUserIdsField.getText();
        current.dingToChat = dingToChatCheck.isSelected();
        list.repaint();
    }

    private void saveSecret() {
        if (suppress || current == null) {
            return;
        }
        char[] arr;
        if (current.type == ChannelType.DINGTALK_REPORT) {
            arr = dingAppSecretField.getPassword();
        } else {
            arr = secretField.getPassword();
        }
        String key = current.secretKey();
        secretDrafts.put(key, arr == null ? "" : new String(arr));
        deletedSecretKeys.remove(key);
    }

    public JComponent getRoot() {
        return root;
    }

    public List<ChannelConfig> getData() {
        List<ChannelConfig> out = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            out.add(listModel.get(i));
        }
        return out;
    }

    public Map<String, String> getSecretDrafts() {
        return new LinkedHashMap<>(secretDrafts);
    }

    public Set<String> getDeletedSecretKeys() {
        return new LinkedHashSet<>(deletedSecretKeys);
    }

    public boolean hasSecretChanges() {
        for (Map.Entry<String, String> entry : secretDrafts.entrySet()) {
            String persisted = SecureKeyStore.load(entry.getKey());
            String draft = entry.getValue() == null ? "" : entry.getValue();
            if (!persisted.equals(draft)) {
                return true;
            }
        }
        for (String key : deletedSecretKeys) {
            if (!SecureKeyStore.load(key).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void setData(List<ChannelConfig> data) {
        secretDrafts.clear();
        deletedSecretKeys.clear();
        listModel.clear();
        if (data != null) {
            for (ChannelConfig ch : data) {
                if (ch != null) listModel.addElement(ch);
            }
        }
        if (listModel.size() > 0) {
            list.setSelectedIndex(0);
        } else {
            loadDetail(null);
        }
    }

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        SimpleDocumentListener(Runnable action) { this.action = action; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }
}
