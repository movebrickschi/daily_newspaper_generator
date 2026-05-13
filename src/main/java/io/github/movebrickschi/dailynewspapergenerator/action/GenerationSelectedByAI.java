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
 * 润色选中分支的提交记录（流式）。
 *
 * @author Liu Chunchi
 */
public class GenerationSelectedByAI extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        String selected = ExtractSelectedAction.getSelectedCommits(e);
        ReportActionRunner.runInBackground(project, "正在润色选中提交...", "AI 润色失败", indicator -> {
            LlmSettings settings = LlmSettings.getInstance();
            boolean stream = settings == null || settings.enableStream;
            if (stream) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ReportDialog dialog = ReportDialogs.showStreamable(
                            project, "选中提交 AI 润色", selected, "");
                    String prompt = settings == null ? null : settings.resolveActivePrompt();
                    dialog.startStreaming(selected, prompt);
                });
            } else {
                String polished = LlmUtil.polish(selected,
                        settings == null ? null : settings.resolveActivePrompt());
                ApplicationManager.getApplication().invokeLater(() ->
                        ReportDialogs.show(project, "选中提交 AI 润色", polished));
            }
        });
    }
}
