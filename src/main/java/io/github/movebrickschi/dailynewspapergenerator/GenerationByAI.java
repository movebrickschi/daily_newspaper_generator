package io.github.movebrickschi.dailynewspapergenerator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GenerationByAI extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在生成日报内容...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在使用AI润色内容，请稍候...");
                indicator.setIndeterminate(true);


                // 生成日报内容
                String dailyReport = Generation.getSelectedCommits(e);

                dailyReport = ZhipuUtil.polish(dailyReport);

                // 使用 ProgressManager 在后台线程执行耗时操作
                String polishedReport = ZhipuUtil.polish(dailyReport);

                ApplicationManager.getApplication().invokeLater(() ->
                        Generation.showReportInDialog(project, polishedReport));
            }
        });
    }


}
