package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.channel.ChannelSenderRegistry;
import io.github.movebrickschi.dailynewspapergenerator.channel.SendResult;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettingsConfigurable;
import io.github.movebrickschi.dailynewspapergenerator.i18n.DailyReportBundle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ReportDialog} 的「推送」流程辅助。
 * <p>
 * 把「BGT 调用 {@link ChannelSenderRegistry} + EDT 上回写状态栏 + 失败 / 成功 notification」
 * 这一整段逻辑从 dialog 中剥离，dialog 仅负责拼参数并提供 UI 回调（启停按钮、状态显示）。
 *
 * @author Liu Chunchi
 */
final class ReportPushHelper {

    private static final String GROUP = "DailyReportGroup";

    private ReportPushHelper() {
    }

    /**
     * 异步执行一次推送。注意：本方法在 EDT 上调用，自己处理 BGT/EDT 切换。
     *
     * @param project        当前项目，用于绑定通知作用域 + 打开设置
     * @param ch             选中的通道配置
     * @param title          报告标题（用作 sender 消息 title）
     * @param content        报告正文 markdown
     * @param statusNotifier 状态栏托管，方法负责切换 running/success/error 状态
     * @param onComplete     EDT 上回调（无论成功失败），用于解锁推送按钮等收尾动作
     * @param onRetry        通知中「重试」按钮被点击时执行（一般为再次调本方法）
     */
    static void executePush(@NotNull Project project,
                            @NotNull ChannelConfig ch,
                            @NotNull String title,
                            @NotNull String content,
                            @NotNull StatusNotifier statusNotifier,
                            @NotNull Runnable onComplete,
                            @NotNull Runnable onRetry) {
        statusNotifier.running(DailyReportBundle.message("status.pushing", ch.name));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SendResult result;
            try {
                result = ChannelSenderRegistry.send(ch, title, content);
            } catch (Exception ex) {
                result = SendResult.failure("Sender 异常: " + ex.getMessage());
            }
            final SendResult finalResult = result;
            ApplicationManager.getApplication().invokeLater(() -> {
                onComplete.run();
                if (finalResult.success()) {
                    statusNotifier.success(DailyReportBundle.message("status.push.success", ch.name));
                    notifyInfo(project,
                            DailyReportBundle.message("notify.send.success.title"),
                            ch.name + " 已收到日报");
                } else {
                    statusNotifier.error(DailyReportBundle.message("status.push.failed",
                            ch.name, finalResult.message()));
                    notifyError(project,
                            DailyReportBundle.message("notify.send.failure.title") + " → " + ch.name,
                            finalResult.message(),
                            onRetry);
                }
            });
        });
    }

    private static void notifyInfo(@NotNull Project project,
                                   @NotNull String title,
                                   @NotNull String content) {
        Notifications.Bus.notify(
                new Notification(GROUP, title, content, NotificationType.INFORMATION),
                project);
    }

    private static void notifyError(@NotNull Project project,
                                    @NotNull String title,
                                    @NotNull String content,
                                    @NotNull Runnable onRetry) {
        Notification n = new Notification(GROUP, title, content, NotificationType.ERROR);
        n.addAction(NotificationAction.createSimple(
                DailyReportBundle.message("notify.retry"), onRetry));
        n.addAction(NotificationAction.createSimple(
                DailyReportBundle.message("notify.openSettings"),
                (Runnable) () -> ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, LlmSettingsConfigurable.class)));
        Notifications.Bus.notify(n, project);
    }
}
