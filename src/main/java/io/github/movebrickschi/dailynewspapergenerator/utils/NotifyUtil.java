package io.github.movebrickschi.dailynewspapergenerator.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettingsConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 统一的通知工具，所有错误 notification 自带「打开设置」入口。
 *
 * @author Liu Chunchi
 */
public final class NotifyUtil {

    public static final String GROUP = "DailyReportGroup";

    private NotifyUtil() {
    }

    public static void info(@Nullable Project project, @NotNull String title, @NotNull String content) {
        Notifications.Bus.notify(new Notification(GROUP, title, content, NotificationType.INFORMATION), project);
    }

    public static void warn(@Nullable Project project, @NotNull String title, @NotNull String content) {
        Notifications.Bus.notify(new Notification(GROUP, title, content, NotificationType.WARNING), project);
    }

    public static void error(@Nullable Project project, @NotNull String title, @NotNull String content) {
        Notification n = new Notification(GROUP, title, content, NotificationType.ERROR);
        n.addAction(NotificationAction.createSimple("打开设置",
                (Runnable) () -> ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, LlmSettingsConfigurable.class)));
        Notifications.Bus.notify(n, project);
    }
}
