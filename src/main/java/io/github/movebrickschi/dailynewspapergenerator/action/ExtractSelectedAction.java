package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 提取选中分支的提交记录
 *
 * @author Liu Chunchi
 */
public class ExtractSelectedAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;


        // 生成日报内容
        String dailyReport = getSelectedCommits(e);

        // 显示结果
        showReportInDialog(project, dailyReport);
    }

    public static String getSelectedCommits(AnActionEvent e) {

        VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        List<VcsFullCommitDetails> cachedFullDetails = selection.getCachedFullDetails();

        StringBuilder report = new StringBuilder();
        report.append("# 记录列表\n\n");

        for (VcsFullCommitDetails commit : cachedFullDetails) {
            report.append("- ").append(commit.getFullMessage()).append("\n\n");
        }

        return report.toString();
    }

    public static void showReportInDialog(Project project, String report) {
        // 使用 DialogWrapper 创建可调整大小的对话框
        DailyReportDialog dialog = new DailyReportDialog(project, report);
        dialog.show();
    }

    // 新增一个内部类来处理对话框
    public static class DailyReportDialog extends DialogWrapper {
        private final String reportContent;
        private final Project project;  // 添加 project 成员变量

        protected DailyReportDialog(@Nullable Project project, String report) {
            super(project);
            this.project = project;       // 初始化 project 成员变量
            this.reportContent = report;
            setTitle("Daily Report");
            setModal(true);
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JTextArea textArea = new JTextArea(reportContent);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            return scrollPane;
        }

        @Override
        protected void doOKAction() {
            copyToClipboard(project, reportContent);  // 使用成员变量 project
            super.doOKAction();
        }

        @Override
        protected Action @NotNull [] createActions() {
            return new Action[]{getOKAction(), getCancelAction()};
        }

        @Override
        public void doCancelAction() {
            super.doCancelAction();
        }
    }

    public static void copyToClipboard(Project project, String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            Messages.showMessageDialog(project, "已复制到剪贴板", "提示", Messages.getInformationIcon());
        } catch (Exception e) {
            Messages.showMessageDialog(project, "复制失败: " + e.getMessage(), "错误", Messages.getErrorIcon());
        }
    }


}
