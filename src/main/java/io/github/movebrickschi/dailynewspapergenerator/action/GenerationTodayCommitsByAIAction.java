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
                    String todaysCommits = getTodaysCommits(project);
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

    private String getTodaysCommits(Project project) {
        try {
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                throw new RuntimeException("无法获取项目路径");
            }

            // 先获取当前用户的 Git 用户名
            String gitUserName = getGitUserName(projectPath);

            ProcessBuilder processBuilder = new ProcessBuilder();
            // 使用获取到的实际用户名而不是 shell 命令替换
            processBuilder.command("git", "log", "--since=00:00:00",
                    "--author=" + gitUserName,
                    "--pretty=format:%s", "--date=short");
            processBuilder.directory(new File(projectPath));

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorResult = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorResult.append(line).append("\n");
                }
                throw new RuntimeException("Git命令执行失败: " + errorResult.toString());
            }

            if (result.length() == 0) {
                return "今天没有提交记录";
            }

            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("获取提交记录失败: " + e.getMessage(), e);
        }
    }

    private String getGitUserName(String projectPath) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("git", "config", "user.name");
        processBuilder.directory(new File(projectPath));

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String userName = reader.readLine();

        int exitCode = process.waitFor();
        if (exitCode != 0 || userName == null || userName.trim().isEmpty()) {
            // 如果无法获取用户名，使用通配符匹配所有提交
            return ".*";
        }

        return userName.trim();
    }

    private void showCommitsInDialog(Project project, String commits) {
        Messages.showMessageDialog(
                project,
                commits,
                "今日提交记录",
                Messages.getInformationIcon()
        );
    }
}
