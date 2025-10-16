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
import com.intellij.openapi.ui.Messages;
import io.github.movebrickschi.dailynewspapergenerator.utils.ZhipuUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class GenerationTodayCommitsByAIAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在生成日报内容...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("正在生成今日日报，请稍候...");
                    indicator.setIndeterminate(true);
                    // 获取今天的提交记录
                    String todaysCommits = ExtractTodayCommitsAction.getTodaysCommits(project);
                    // 使用 ProgressManager 在后台线程执行耗时操作
                    String polishedReport = ZhipuUtil.polish(todaysCommits);

                    ApplicationManager.getApplication().invokeLater(() ->
                            ExtractSelectedAction.showReportInDialog(project, polishedReport));
                } catch (Exception ex) {
                    // 异常处理，避免插件卡死
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 显示错误提示
                        Notification notification = new Notification(
                                "DailyReportGroup",
                                "日报生成失败",
                                "生成过程中出现错误: " + ex.getMessage(),
                                NotificationType.ERROR
                        );
                        Notifications.Bus.notify(notification, project);
                    });
                }
            }
        });
    }

}
