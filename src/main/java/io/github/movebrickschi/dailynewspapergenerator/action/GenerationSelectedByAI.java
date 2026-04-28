package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialog;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialogV2;
import io.github.movebrickschi.dailynewspapergenerator.utils.LlmUtil;
import io.github.movebrickschi.dailynewspapergenerator.utils.NotifyUtil;
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
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在润色选中提交...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(true);
                    LlmSettings settings = LlmSettings.getInstance();
                    boolean stream = settings == null || settings.enableStream;
                    if (stream) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            ReportDialog dialog = ReportDialogV2.showStreamable(
                                    project, "选中提交 AI 润色", selected, "");
                            String prompt = settings == null ? null : settings.resolveActivePrompt();
                            dialog.startStreaming(selected, prompt);
                        });
                    } else {
                        String polished = LlmUtil.polish(selected,
                                settings == null ? null : settings.resolveActivePrompt());
                        ApplicationManager.getApplication().invokeLater(() ->
                                ReportDialogV2.show(project, "选中提交 AI 润色", polished));
                    }
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            NotifyUtil.error(project, "AI 润色失败",
                                    "AI润色过程中出现错误: " + ex.getMessage()));
                }
            }
        });
    }
}
