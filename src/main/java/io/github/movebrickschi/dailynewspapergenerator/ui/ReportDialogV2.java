package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 报告展示对话框 V2 入口（详细实现见 P3 阶段）。
 * <p>
 * 这里先提供一个静态 show 方法，供其它 Action 提前使用；具体可编辑/Markdown 预览/再润色/导出
 * 等能力会在 P3 阶段加在 {@link ReportDialog} 里。
 *
 * @author Liu Chunchi
 */
public final class ReportDialogV2 {

    private ReportDialogV2() {
    }

    /** 展示报告。报告内容使用 markdown 格式。 */
    public static void show(@NotNull Project project, @NotNull String title, @NotNull String markdown) {
        new ReportDialog(project, title, markdown, null, null).show();
    }

    /** 展示流式 / 可润色的报告。提供原始内容（润色前），方便「再润色」按钮使用。 */
    public static ReportDialog showStreamable(@NotNull Project project,
                                              @NotNull String title,
                                              @NotNull String rawSource,
                                              @NotNull String initialMarkdown) {
        ReportDialog dialog = new ReportDialog(project, title, initialMarkdown, rawSource, null);
        dialog.show();
        return dialog;
    }
}
