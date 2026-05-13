package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 报告对话框统一构造门面。
 * <p>
 * 所有 Action 通过这里创建 {@link ReportDialog} 实例，隐藏构造参数细节，保持调用方简洁。
 * 旧入口 {@link ReportDialogV2} 已 deprecated，其方法会 forward 到本类，保留以避免 fork
 * 依赖断裂。
 *
 * @author Liu Chunchi
 */
public final class ReportDialogs {

    private ReportDialogs() {
    }

    /** 展示一份只读 / 可编辑的 markdown 报告，立刻 modal-less 显示。 */
    public static void show(@NotNull Project project, @NotNull String title, @NotNull String markdown) {
        new ReportDialog(project, title, markdown, null, null).show();
    }

    /**
     * 展示一个支持流式接收 / 「再润色」的报告对话框。
     *
     * @param rawSource       原始 commit 文本（润色前），用于「再润色」按钮重新发起请求
     * @param initialMarkdown 对话框初始显示内容，常常传 "" 让流式过程填充
     * @return 已 show() 的对话框实例，调用方可后续调 {@code startStreaming(...)}
     */
    public static ReportDialog showStreamable(@NotNull Project project,
                                              @NotNull String title,
                                              @NotNull String rawSource,
                                              @NotNull String initialMarkdown) {
        ReportDialog dialog = new ReportDialog(project, title, initialMarkdown, rawSource, null);
        dialog.show();
        return dialog;
    }
}
