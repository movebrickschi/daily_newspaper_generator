package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialogV2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 从 VCS Log 选中提交并展示。
 *
 * @author Liu Chunchi
 */
public class ExtractSelectedAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        String dailyReport = getSelectedCommits(e);
        showReportInDialog(project, dailyReport);
    }

    public static String getSelectedCommits(AnActionEvent e) {
        VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        StringBuilder report = new StringBuilder();
        report.append("# 选中的提交\n\n");
        if (selection == null) {
            report.append("_未识别到 VCS Log 选区_\n");
            return report.toString();
        }
        List<VcsFullCommitDetails> cachedFullDetails = selection.getCachedFullDetails();
        if (cachedFullDetails == null || cachedFullDetails.isEmpty()) {
            report.append("_未选中任何提交_\n");
            return report.toString();
        }
        for (VcsFullCommitDetails commit : cachedFullDetails) {
            report.append("- ").append(commit.getFullMessage().trim().replace("\n", "\n  ")).append("\n");
        }
        return report.toString();
    }

    /**
     * 兼容旧调用：现在统一走 {@link ReportDialogV2}。
     */
    public static void showReportInDialog(Project project, String report) {
        ReportDialogV2.show(project, "选中的提交", report);
    }
}
