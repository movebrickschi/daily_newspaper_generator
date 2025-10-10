package io.github.movebrickschi.dailynewspapergenerator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;

import java.util.List;

public class Generation extends AnAction {

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
        report.append("# 记录总结\n\n");

        for (VcsFullCommitDetails commit : cachedFullDetails) {
            report.append("- ").append(commit.getFullMessage()).append("\n");
        }

        return report.toString();
    }

    public static void showReportInDialog(Project project, String report) {
        String[] options = {"复制到剪贴板", "关闭"};
        int result = Messages.showDialog(
                project,
                report,
                "Daily Report",
                options,
                // 默认选中第一个按钮
                0,
                Messages.getInformationIcon()
        );

        // 如果用户点击了"复制到剪贴板"按钮
        if (result == 0) {
            copyToClipboard(project, report);
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
