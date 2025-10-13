package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.utils.ZhipuUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 润色选中分支的提交记录
 *
 * @author Liu Chunchi
 */
public class GenerationSelectedByAI extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在生成日报内容...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                indicator.setText("正在使用AI润色内容，请稍候...");
                indicator.setIndeterminate(true);
                // 生成日报内容
                    String dailyReport = ExtractSelectedAction.getSelectedCommits(e);
                // 使用 ProgressManager 在后台线程执行耗时操作
                String polishedReport = ZhipuUtil.polish(dailyReport);

                ApplicationManager.getApplication().invokeLater(() ->
                        ExtractSelectedAction.showReportInDialog(project, polishedReport));
                } catch (Exception ex) {
                    // 异常处理，避免插件卡死
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 显示错误提示
                        Notification notification = new Notification(
                                "DailyReportGroup",
                                "日报生成失败",
                                "AI润色过程中出现错误: " + ex.getMessage(),
                                NotificationType.ERROR
                        );
                        Notifications.Bus.notify(notification, project);
                    });
                }
            }
        });
    }


}
