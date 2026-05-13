package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialog;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialogs;
import io.github.movebrickschi.dailynewspapergenerator.utils.LlmUtil;
import io.github.movebrickschi.dailynewspapergenerator.utils.ReportActionRunner;
import org.jetbrains.annotations.NotNull;

/**
 * 生成今日 AI 润色日报。流式渲染到 {@link ReportDialog}。
 *
 * @author Liu Chunchi
 */
public class GenerationTodayCommitsByAIAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        ReportActionRunner.runInBackground(project, "正在抽取今日提交...", "日报生成失败", indicator -> {
            indicator.setText("正在抽取今日提交...");
            String commits = ExtractTodayCommitsAction.getTodaysCommits(project);
            LlmSettings settings = LlmSettings.getInstance();
            boolean stream = settings == null || settings.enableStream;
            if (stream) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ReportDialog dialog = ReportDialogs.showStreamable(
                            project, "今日 AI 日报", commits, "");
                    String prompt = settings == null ? null : settings.resolveActivePrompt();
                    dialog.startStreaming(commits, prompt);
                });
            } else {
                String polished = LlmUtil.polish(commits,
                        settings == null ? null : settings.resolveActivePrompt());
                ApplicationManager.getApplication().invokeLater(() ->
                        ReportDialogs.show(project, "今日 AI 日报", polished));
            }
        });
    }
}
