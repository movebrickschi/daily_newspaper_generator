package io.github.movebrickschi.dailynewspapergenerator.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 后台型 Report Action 的通用执行入口，用于消除四个 Action（今日抽取 / 范围抽取 /
 * 今日 AI / 选中 AI）几乎相同的 {@code ProgressManager.run(Task.Backgroundable)}
 * + try/catch + {@link NotifyUtil#error} 的模板代码。
 *
 * <p>用法：
 * <pre>{@code
 * ReportActionRunner.runInBackground(project, "正在抽取...", "抽取失败", indicator -> {
 *     String md = doExtract();
 *     ApplicationManager.getApplication().invokeLater(() ->
 *             ReportDialogs.show(project, "今日提交", md));
 * });
 * }</pre>
 *
 * @author Liu Chunchi
 */
public final class ReportActionRunner {

    private ReportActionRunner() {
    }

    /**
     * 以 {@link Task.Backgroundable} 提交一段工作；进度条默认 {@code indeterminate}。
     * 业务异常通过 {@link NotifyUtil#error} 在 EDT 上展示，带「打开设置」入口。
     *
     * @param project    当前项目（必填，{@code null} 时方法不做任何事）
     * @param taskTitle  进度条上的标题
     * @param errorTitle 异常时通知的标题
     * @param body       业务逻辑；在 BGT 上执行；可抛任何异常
     */
    public static void runInBackground(Project project,
                                       @NotNull String taskTitle,
                                       @NotNull String errorTitle,
                                       @NotNull BackgroundBody body) {
        if (project == null) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, taskTitle, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(true);
                    body.execute(indicator);
                } catch (Throwable ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            NotifyUtil.error(project, errorTitle,
                                    "执行过程中出现错误: " + safeMessage(ex)));
                }
            }
        });
    }

    private static String safeMessage(Throwable ex) {
        if (ex == null) {
            return "(null)";
        }
        String msg = ex.getMessage();
        return msg == null ? ex.getClass().getSimpleName() : msg;
    }

    /** 后台工作体；可抛任意异常，由 {@link ReportActionRunner} 统一通知。 */
    @FunctionalInterface
    public interface BackgroundBody {
        void execute(@NotNull ProgressIndicator indicator) throws Exception;
    }
}
