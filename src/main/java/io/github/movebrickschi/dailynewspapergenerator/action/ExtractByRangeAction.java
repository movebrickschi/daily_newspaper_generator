package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.movebrickschi.dailynewspapergenerator.ui.ExtractRangeDialog;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialogs;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExtractOptions;
import io.github.movebrickschi.dailynewspapergenerator.utils.GitCommitExtractor;
import io.github.movebrickschi.dailynewspapergenerator.utils.ReportActionRunner;
import org.jetbrains.annotations.NotNull;

/**
 * 弹出 {@link ExtractRangeDialog} 让用户选择日期范围 / 作者 / 过滤项，然后执行抽取并展示。
 *
 * @author Liu Chunchi
 */
public class ExtractByRangeAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        ExtractRangeDialog dialog = new ExtractRangeDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }
        ExtractOptions options = dialog.toOptions();
        if (options == null) {
            Messages.showWarningDialog(project,
                    "自定义日期格式不正确，请使用 yyyy-MM-dd",
                    "日报生成器");
            return;
        }
        ReportActionRunner.runInBackground(project, "正在抽取提交记录...", "抽取失败", indicator -> {
            GitCommitExtractor.Result result = GitCommitExtractor.extract(project, options);
            ApplicationManager.getApplication().invokeLater(() ->
                    ReportDialogs.show(project, "提交记录", result.markdown()));
        });
    }
}
