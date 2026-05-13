package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettingsListener;
import io.github.movebrickschi.dailynewspapergenerator.config.PromptTemplate;
import io.github.movebrickschi.dailynewspapergenerator.i18n.DailyReportBundle;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExportService;
import io.github.movebrickschi.dailynewspapergenerator.utils.LlmUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 报告展示对话框。统一通过 {@link ReportDialogs} 静态门面构造。
 * <ul>
 *   <li>可编辑的 markdown 文本区</li>
 *   <li>markdown 预览（基于 JEditorPane HTML 渲染）</li>
 *   <li>底部三区布局：左 = 模板/通道下拉；中 = 状态栏 + 进度条；右 = 动作按钮</li>
 *   <li>支持流式接收 LLM 响应，可中断</li>
 *   <li>快捷键：Esc 关闭、Ctrl+Enter 推送</li>
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
    private final JProgressBar progressBar = new JProgressBar();
    private final StatusNotifier statusNotifier;
    private final ComboBox<PromptTemplate> templateCombo = new ComboBox<>();
    private final ComboBox<ChannelConfig> channelCombo = new ComboBox<>();
    private final JButton copyBtn = ReportButtonFactory.primary(
            DailyReportBundle.message("dialog.copy"), UiTokens.Icons.COPY,
            DailyReportBundle.message("dialog.copy.tooltip"));
    private final JButton exportBtn = ReportButtonFactory.secondary(
            DailyReportBundle.message("dialog.export"), UiTokens.Icons.EXPORT,
            DailyReportBundle.message("dialog.export.tooltip"));
    private final JButton pushBtn = ReportButtonFactory.mainAction(
            DailyReportBundle.message("dialog.push"), UiTokens.Icons.PUSH,
            DailyReportBundle.message("dialog.push.tooltip"));
    private final JButton repolishBtn = ReportButtonFactory.secondary(
            DailyReportBundle.message("dialog.repolish"), UiTokens.Icons.REPOLISH,
            DailyReportBundle.message("dialog.repolish.tooltip"));
    private final JButton closeBtn = ReportButtonFactory.secondary(
            DailyReportBundle.message("dialog.close"), UiTokens.Icons.CLOSE,
            DailyReportBundle.message("dialog.close.tooltip"));

    /** 原始 commit 内容；非空时启用「再润色」。 */
    @Nullable
    private final String rawSource;

    /** 当前流式任务的取消信号，可在外部置 true 来停止流式接收。 */
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private boolean streaming = false;

    /** 预览 debounce 定时器，200ms 内的连续编辑/SSE delta 只触发一次预览重渲。 */
    private final javax.swing.Timer previewDebounceTimer;

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
        previewDebounceTimer = new javax.swing.Timer(200, e -> renderPreviewNow());
        previewDebounceTimer.setRepeats(false);
        editArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::schedulePreview));
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(JBUI.scale(120), JBUI.scale(4)));
        progressBar.setBorder(JBUI.Borders.empty(0, 8));
        statusNotifier = new StatusNotifier(statusLabel, progressBar);
        init();
        renderPreviewNow();
        refreshTemplates();
        refreshChannels();
        repolishBtn.setVisible(rawSource != null && !rawSource.isBlank());
        subscribeSettingsChanged();
    }

    /**
     * 订阅 {@link LlmSettingsListener}：用户在设置页保存后自动刷新模板 / 通道下拉。
     * 通过 {@link #getDisposable()} 关联对话框生命周期，对话框关闭时自动取消订阅。
     */
    private void subscribeSettingsChanged() {
        try {
            ApplicationManager.getApplication().getMessageBus()
                    .connect(getDisposable())
                    .subscribe(LlmSettingsListener.TOPIC, () -> ApplicationManager.getApplication().invokeLater(() -> {
                        refreshTemplates();
                        refreshChannels();
                    }));
        } catch (Throwable ignored) {
            // 订阅失败不影响对话框正常使用，只是失去自动刷新能力
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab(DailyReportBundle.message("dialog.tab.edit"), new JBScrollPane(editArea));
        previewPane.setEditable(false);
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                BrowserUtil.browse(e.getURL());
            }
        });
        tabs.addTab(DailyReportBundle.message("dialog.tab.preview"), new JBScrollPane(previewPane));

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(UiTokens.Sizes.dialogWidth(), UiTokens.Sizes.dialogHeight()));
        registerShortcuts(root);
        return root;
    }

    @Override
    protected JComponent createSouthPanel() {
        // 顶部行：模板 + 通道 + 状态 + 进度条
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTokens.Sizes.gap(), 0));
        JBLabel templateLabel = new JBLabel(DailyReportBundle.message("dialog.template.label") + ":");
        templateCombo.setToolTipText(DailyReportBundle.message("dialog.template.tooltip"));
        JBLabel channelLabel = new JBLabel(DailyReportBundle.message("dialog.channel.label") + ":");
        channelCombo.setToolTipText(DailyReportBundle.message("dialog.channel.tooltip"));
        topLeft.add(templateLabel);
        topLeft.add(templateCombo);
        topLeft.add(channelLabel);
        topLeft.add(channelCombo);

        JPanel topRight = new JPanel(new BorderLayout(UiTokens.Sizes.gap(), 0));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        topRight.add(statusLabel, BorderLayout.CENTER);
        topRight.add(progressBar, BorderLayout.EAST);

        JPanel topRow = new JPanel(new BorderLayout(UiTokens.Sizes.gapLg(), 0));
        topRow.add(topLeft, BorderLayout.WEST);
        topRow.add(topRight, BorderLayout.CENTER);

        // 底部行：按钮区（次要按钮靠左，主要按钮靠右；中间用 separator 分割视觉权重）
        JPanel bottomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTokens.Sizes.gap(), 0));
        closeBtn.addActionListener(e -> doCancelAction());
        exportBtn.addActionListener(e -> doExport());
        repolishBtn.addActionListener(e -> doRepolish());
        bottomLeft.add(closeBtn);
        bottomLeft.add(exportBtn);
        bottomLeft.add(repolishBtn);

        JPanel bottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTokens.Sizes.gap(), 0));
        copyBtn.addActionListener(e -> doCopy());
        pushBtn.addActionListener(e -> doPush());
        bottomRight.add(copyBtn);
        bottomRight.add(pushBtn);

        JPanel bottomRow = new JPanel(new BorderLayout(UiTokens.Sizes.gapLg(), 0));
        bottomRow.add(bottomLeft, BorderLayout.WEST);
        bottomRow.add(bottomRight, BorderLayout.EAST);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(topRow);
        south.add(Box.createVerticalStrut(JBUI.scale(6)));
        south.add(bottomRow);
        south.setBorder(JBUI.Borders.empty(8));
        return south;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[0];
    }

    private void registerShortcuts(@NotNull JComponent root) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dr.close");
        am.put("dr.close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doCancelAction();
            }
        });
        int ctrl = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, ctrl), "dr.push");
        am.put("dr.push", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pushBtn.isEnabled()) {
                    doPush();
                }
            }
        });
    }

    private void schedulePreview() {
        // 流式期间禁用预览 debounce：流式 delta 高频触发，HTML 全量 reparse + setText
        // 会卡 EDT 且观感意义不大。改为流式结束后统一渲染一次。
        if (streaming) {
            return;
        }
        if (previewDebounceTimer != null) {
            previewDebounceTimer.restart();
        }
    }

    private void renderPreviewNow() {
        String md = editArea.getText();
        previewPane.setText(MarkdownEngines.current().toHtml(md));
        previewPane.setCaretPosition(0);
    }

    private void refreshTemplates() {
        templateCombo.removeAllItems();
        LlmSettings settings = LlmSettings.getInstance();
        if (settings == null) {
            return;
        }
        PromptTemplate def = new PromptTemplate(DailyReportBundle.message("templates.preset.blank"),
                settings.promptTemplate);
        def.id = "";
        templateCombo.addItem(def);
        if (settings.templates != null) {
            for (PromptTemplate t : settings.templates) {
                if (t != null) templateCombo.addItem(t);
            }
        }
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
        if (settings != null && settings.channels != null) {
            for (ChannelConfig ch : settings.channels) {
                if (ch != null) channelCombo.addItem(ch);
            }
        }
        boolean hasChannels = channelCombo.getItemCount() > 0;
        pushBtn.setEnabled(hasChannels);
        if (!hasChannels) {
            // 空通道列表时给一个引导式 tooltip + 状态提示，点击 push 时引导用户去设置
            pushBtn.setToolTipText(DailyReportBundle.message("dialog.push.empty.tooltip"));
            statusNotifier.info(DailyReportBundle.message("status.channels.empty"));
        } else {
            pushBtn.setToolTipText(DailyReportBundle.message("dialog.push.tooltip"));
        }
    }

    private void doCopy() {
        try {
            String md = editArea.getText();
            String html = MarkdownEngines.current().toHtml(md);
            CopyPasteManager.getInstance().setContents(new MarkdownHtmlTransferable(md, html));
            statusNotifier.success(DailyReportBundle.message("status.copied"));
        } catch (Exception ex) {
            statusNotifier.error(DailyReportBundle.message("status.copy.failed", ex.getMessage()));
        }
    }

    private void doExport() {
        try {
            File file = ExportService.exportMarkdown(project, editArea.getText(), title);
            statusNotifier.success(DailyReportBundle.message("status.exported", file.getAbsolutePath()));
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (vf != null) {
                FileEditorManager.getInstance(project).openFile(vf, true);
            }
        } catch (IOException ex) {
            statusNotifier.error(DailyReportBundle.message("status.export.failed", ex.getMessage()));
        }
    }

    private void doPush() {
        ChannelConfig ch = (ChannelConfig) channelCombo.getSelectedItem();
        if (ch == null) {
            statusNotifier.error(DailyReportBundle.message("status.no.channel"));
            return;
        }
        String content = editArea.getText();
        pushBtn.setEnabled(false);
        // 把 push 按钮临时换为 spinner 图标 + 改文案，结束后恢复
        final javax.swing.Icon originalIcon = pushBtn.getIcon();
        final String originalText = pushBtn.getText();
        pushBtn.setIcon(new com.intellij.ui.AnimatedIcon.Default());
        pushBtn.setText(DailyReportBundle.message("status.pushing", ch.name));
        ReportPushHelper.executePush(project, ch, title, content, statusNotifier,
                () -> {
                    pushBtn.setEnabled(true);
                    pushBtn.setIcon(originalIcon);
                    pushBtn.setText(originalText);
                },
                this::doPush);
    }

    private void doRepolish() {
        if (rawSource == null || rawSource.isBlank()) {
            return;
        }
        if (streaming) {
            cancelFlag.set(true);
            statusNotifier.info(DailyReportBundle.message("status.streaming.cancelling"));
            return;
        }
        PromptTemplate selected = (PromptTemplate) templateCombo.getSelectedItem();
        String prompt = selected == null ? null : selected.content;
        startStream(rawSource, prompt);
    }

    private void startStream(@NotNull String content, @Nullable String promptOverride) {
        editArea.setText("");
        cancelFlag.set(false);
        streaming = true;
        repolishBtn.setText(DailyReportBundle.message("dialog.stop"));
        repolishBtn.setIcon(UiTokens.Icons.STOP);
        repolishBtn.setToolTipText(DailyReportBundle.message("dialog.stop.tooltip"));
        statusNotifier.running(DailyReportBundle.message("status.polishing"));
        StringBuilder buf = new StringBuilder();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ok = LlmUtil.polishStream(content, promptOverride, delta -> {
                buf.append(delta);
                int len = buf.length();
                ApplicationManager.getApplication().invokeLater(() -> {
                    editArea.append(delta);
                    statusNotifier.running(DailyReportBundle.message("status.polishing.received", len));
                });
            }, cancelFlag::get);
            ApplicationManager.getApplication().invokeLater(() -> {
                streaming = false;
                repolishBtn.setText(DailyReportBundle.message("dialog.repolish"));
                repolishBtn.setIcon(UiTokens.Icons.REPOLISH);
                repolishBtn.setToolTipText(DailyReportBundle.message("dialog.repolish.tooltip"));
                if (ok) {
                    statusNotifier.success(DailyReportBundle.message("status.polish.completed", buf.length()));
                } else {
                    statusNotifier.error(DailyReportBundle.message("status.polish.failed"));
                }
                // 流式期间预览渲染被 schedulePreview 抑制，结束后一次性补一次。
                renderPreviewNow();
            });
        });
    }

    @Override
    public void doCancelAction() {
        // 流式期间 Esc / 关闭按钮只停流，不关闭对话框；用户可以保留已生成的部分内容继续编辑/推送。
        if (streaming) {
            cancelFlag.set(true);
            statusNotifier.info(DailyReportBundle.message("status.streaming.cancelling"));
            return;
        }
        cancelFlag.set(true);
        super.doCancelAction();
    }

    /** 在外部启动一次流式接收。供 GenerationXxxAction 使用。 */
    public void startStreaming(@NotNull String content, @Nullable String promptOverride) {
        startStream(content, promptOverride);
    }

    private static boolean safeEq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
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
