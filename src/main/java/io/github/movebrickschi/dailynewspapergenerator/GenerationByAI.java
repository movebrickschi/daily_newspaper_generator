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


        // 生成日报内容
        String dailyReport = Generation.getSelectedCommits(e);

        dailyReport = ZhipuUtil.polish(dailyReport);

        // 使用 ProgressManager 在后台线程执行耗时操作
        String finalDailyReport = dailyReport;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在生成日报内容...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 设置进度指示器文本
                indicator.setText("正在使用AI润色内容，请稍候...");
                indicator.setIndeterminate(true); // 设置为不确定进度模式

                // 执行耗时的润色操作
                String polishedReport = ZhipuUtil.polish(finalDailyReport);

                // 在EDT线程中显示结果
                ApplicationManager.getApplication().invokeLater(() -> {
                    Generation.showReportInDialog(project, polishedReport);
                });
            }
        });
    }


}
