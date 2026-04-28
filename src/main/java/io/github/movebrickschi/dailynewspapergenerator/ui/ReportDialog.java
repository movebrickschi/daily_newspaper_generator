package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.channel.ChannelSenderRegistry;
import io.github.movebrickschi.dailynewspapergenerator.channel.SendResult;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettingsConfigurable;
import io.github.movebrickschi.dailynewspapergenerator.config.PromptTemplate;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExportService;
import io.github.movebrickschi.dailynewspapergenerator.utils.LlmUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 报告展示对话框（V2）。
 * <ul>
 *   <li>左侧可编辑的 markdown 文本区</li>
 *   <li>右侧 markdown 预览（基于 JEditorPane HTML 渲染，简易）</li>
 *   <li>底部主按钮：复制 / 导出 md / 推送到… / 再润色（可用时）</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class ReportDialog extends DialogWrapper {

    private final Project project;
    private final String title;
    private final JTextArea editArea = new JTextArea();
    private final JEditorPane previewPane = new JEditorPane("text/html", "");
    private final JBLabel statusLabel = new JBLabel(" ");
    private final ComboBox<PromptTemplate> templateCombo = new ComboBox<>();
    private final ComboBox<ChannelConfig> channelCombo = new ComboBox<>();
    private final JButton copyBtn = new JButton("复制");
    private final JButton exportBtn = new JButton("导出 md");
    private final JButton pushBtn = new JButton("推送");
    private final JButton repolishBtn = new JButton("再润色");
    private final JButton closeBtn = new JButton("关闭");

    /** 原始 commit 内容；非空时启用「再润色」。 */
    @Nullable
    private final String rawSource;

    /** 当前流式任务的取消信号，可在外部置 true 来停止流式接收。 */
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private boolean streaming = false;

    public ReportDialog(@NotNull Project project,
                        @NotNull String title,
                        @NotNull String initialMarkdown,
                        @Nullable String rawSource,
                        @SuppressWarnings("unused") @Nullable Object reserved) {
        super(project);
        this.project = project;
        this.title = title;
        this.rawSource = rawSource;
        setTitle(title);
        setModal(false);
        editArea.setText(initialMarkdown == null ? "" : initialMarkdown);
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::refreshPreview));
        init();
        refreshPreview();
        refreshTemplates();
        refreshChannels();
        repolishBtn.setVisible(rawSource != null && !rawSource.isBlank());
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("编辑", new JBScrollPane(editArea));
        previewPane.setEditable(false);
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {
                    // ignore
                }
            }
        });
        tabs.addTab("预览", new JBScrollPane(previewPane));

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(JBUI.scale(720), JBUI.scale(520)));
        return root;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        left.add(new JBLabel("模板:"));
        left.add(templateCombo);
        left.add(new JBLabel("通道:"));
        left.add(channelCombo);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
        copyBtn.addActionListener(e -> doCopy());
        exportBtn.addActionListener(e -> doExport());
        pushBtn.addActionListener(e -> doPush());
        repolishBtn.addActionListener(e -> doRepolish());
        closeBtn.addActionListener(e -> doCancelAction());
        right.add(copyBtn);
        right.add(exportBtn);
        right.add(pushBtn);
        right.add(repolishBtn);
        right.add(closeBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.WEST);
        JPanel inner = new JPanel(new BorderLayout());
        inner.add(left, BorderLayout.WEST);
        inner.add(right, BorderLayout.EAST);
        south.add(inner, BorderLayout.CENTER);
        south.setBorder(JBUI.Borders.empty(8));
        return south;
    }

    @Override
    protected Action @NotNull [] createActions() {
        // 我们用自定义的南侧按钮，因此这里返回空数组，避免出现默认的 OK/Cancel
        return new Action[0];
    }

    private void refreshPreview() {
        String md = editArea.getText();
        previewPane.setText(MarkdownRenderer.toHtml(md));
        previewPane.setCaretPosition(0);
    }

    private void refreshTemplates() {
        templateCombo.removeAllItems();
        LlmSettings settings = LlmSettings.getInstance();
        if (settings == null) {
            return;
        }
        // 在最前面塞一个「默认模板」哨兵
        PromptTemplate def = new PromptTemplate("默认模板", settings.promptTemplate);
        def.id = "";
        templateCombo.addItem(def);
        if (settings.templates != null) {
            for (PromptTemplate t : settings.templates) {
                if (t != null) templateCombo.addItem(t);
            }
        }
        // 选中当前激活的
        for (int i = 0; i < templateCombo.getItemCount(); i++) {
            PromptTemplate t = templateCombo.getItemAt(i);
            if (t != null && safeEq(t.id, settings.activeTemplateId)) {
                templateCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void refreshChannels() {
        channelCombo.removeAllItems();
        LlmSettings settings = LlmSettings.getInstance();
        if (settings == null || settings.channels == null) {
            return;
        }
        for (ChannelConfig ch : settings.channels) {
            if (ch != null) channelCombo.addItem(ch);
        }
        pushBtn.setEnabled(channelCombo.getItemCount() > 0);
    }

    private void doCopy() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(editArea.getText()), null);
            statusLabel.setText("已复制到剪贴板");
        } catch (Exception ex) {
            statusLabel.setText("复制失败: " + ex.getMessage());
        }
    }

    private void doExport() {
        try {
            File file = ExportService.exportMarkdown(project, editArea.getText(), title);
            statusLabel.setText("已导出到 " + file.getAbsolutePath());
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (vf != null) {
                FileEditorManager.getInstance(project).openFile(vf, true);
            }
        } catch (IOException ex) {
            statusLabel.setText("导出失败: " + ex.getMessage());
        }
    }

    private void doPush() {
        ChannelConfig ch = (ChannelConfig) channelCombo.getSelectedItem();
        if (ch == null) {
            statusLabel.setText("请先在 设置 → 日报生成器设置 → 推送通道 中添加通道");
            return;
        }
        String content = editArea.getText();
        pushBtn.setEnabled(false);
        statusLabel.setText("正在推送到 " + ch.name + "...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SendResult result;
            try {
                result = ChannelSenderRegistry.send(ch, title, content);
            } catch (Exception ex) {
                result = SendResult.failure("Sender 异常: " + ex.getMessage());
            }
            final SendResult finalResult = result;
            ApplicationManager.getApplication().invokeLater(() -> {
                pushBtn.setEnabled(true);
                if (finalResult.success()) {
                    statusLabel.setText("推送成功 → " + ch.name);
                    notifyInfo("推送成功", ch.name + " 已收到日报");
                } else {
                    statusLabel.setText("推送失败 → " + ch.name + ": " + finalResult.message());
                    notifyError("推送失败 → " + ch.name, finalResult.message(),
                            new RetryAction(this));
                }
            });
        });
    }

    private void doRepolish() {
        if (rawSource == null || rawSource.isBlank()) {
            return;
        }
        if (streaming) {
            cancelFlag.set(true);
            statusLabel.setText("正在中断当前流...");
            return;
        }
        PromptTemplate selected = (PromptTemplate) templateCombo.getSelectedItem();
        String prompt = selected == null ? null : selected.content;
        editArea.setText("");
        cancelFlag.set(false);
        streaming = true;
        repolishBtn.setText("停止");
        statusLabel.setText("正在润色...");
        StringBuilder buf = new StringBuilder();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ok = LlmUtil.polishStream(rawSource, prompt, delta -> {
                buf.append(delta);
                int len = buf.length();
                ApplicationManager.getApplication().invokeLater(() -> {
                    editArea.append(delta);
                    statusLabel.setText("已接收 " + len + " 字符...");
                });
            }, cancelFlag::get);
            ApplicationManager.getApplication().invokeLater(() -> {
                streaming = false;
                repolishBtn.setText("再润色");
                statusLabel.setText(ok
                        ? "润色完成，共 " + buf.length() + " 字符"
                        : "润色失败或中断");
            });
        });
    }

    @Override
    public void doCancelAction() {
        cancelFlag.set(true);
        super.doCancelAction();
    }

    /** 在外部启动一次流式接收。供 GenerationXxxAction 使用。 */
    public void startStreaming(@NotNull String content, @Nullable String promptOverride) {
        editArea.setText("");
        cancelFlag.set(false);
        streaming = true;
        repolishBtn.setText("停止");
        statusLabel.setText("正在润色...");
        StringBuilder buf = new StringBuilder();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ok = LlmUtil.polishStream(content, promptOverride, delta -> {
                buf.append(delta);
                int len = buf.length();
                ApplicationManager.getApplication().invokeLater(() -> {
                    editArea.append(delta);
                    statusLabel.setText("已接收 " + len + " 字符...");
                });
            }, cancelFlag::get);
            ApplicationManager.getApplication().invokeLater(() -> {
                streaming = false;
                repolishBtn.setText("再润色");
                statusLabel.setText(ok
                        ? "润色完成，共 " + buf.length() + " 字符"
                        : "润色失败或中断");
            });
        });
    }

    private static boolean safeEq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private void notifyInfo(String t, String msg) {
        Notification n = new Notification("DailyReportGroup", t, msg, NotificationType.INFORMATION);
        Notifications.Bus.notify(n, project);
    }

    private void notifyError(String t, String msg, RetryAction retry) {
        Notification n = new Notification("DailyReportGroup", t, msg, NotificationType.ERROR);
        n.addAction(com.intellij.notification.NotificationAction.createSimple("重试",
                (Runnable) retry::run));
        n.addAction(com.intellij.notification.NotificationAction.createSimple("打开设置",
                (Runnable) () -> ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, LlmSettingsConfigurable.class)));
        Notifications.Bus.notify(n, project);
    }

    /** Notification 重试动作的简单实现。 */
    private static class RetryAction {
        private final ReportDialog dialog;
        RetryAction(ReportDialog dialog) { this.dialog = dialog; }
        void run() { dialog.doPush(); }
    }

    /** 文本变化触发重渲的简化监听。 */
    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        SimpleDocumentListener(Runnable action) { this.action = action; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }

    /** 通过当前文本写入磁盘的辅助。 */
    public static void writeText(File file, String text) throws IOException {
        Files.writeString(file.toPath(), text == null ? "" : text, StandardCharsets.UTF_8);
    }

    /** 暴露当前内容（供测试 / 扩展）。 */
    public String getCurrentText() {
        return editArea.getText();
    }

    /** 在不破坏对话框关闭语义的情况下，暴露 size 和 contents (供下游测试)。 */
    @SuppressWarnings("unused")
    public Project getProject() {
        return project;
    }

    /** 列表里如果有 channels 列表也可以访问。 */
    @SuppressWarnings("unused")
    public List<ChannelConfig> getAvailableChannels() {
        java.util.ArrayList<ChannelConfig> list = new java.util.ArrayList<>();
        for (int i = 0; i < channelCombo.getItemCount(); i++) {
            list.add(channelCombo.getItemAt(i));
        }
        return list;
    }
}
