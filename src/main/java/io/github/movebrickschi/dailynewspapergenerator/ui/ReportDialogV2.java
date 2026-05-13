package io.github.movebrickschi.dailynewspapergenerator.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 历史入口：{@code V2} 命名暗示「替代 V1」，但实际只是 {@link ReportDialog} 的
 * 静态构造门面。新代码请使用 {@link ReportDialogs}，本类保留以保证向后兼容。
 *
 * @author Liu Chunchi
 * @deprecated 改用 {@link ReportDialogs}
 */
@Deprecated
public final class ReportDialogV2 {

    private ReportDialogV2() {
    }

    /** @deprecated 改用 {@link ReportDialogs#show(Project, String, String)} */
    @Deprecated
    public static void show(@NotNull Project project, @NotNull String title, @NotNull String markdown) {
        ReportDialogs.show(project, title, markdown);
    }

    /** @deprecated 改用 {@link ReportDialogs#showStreamable(Project, String, String, String)} */
    @Deprecated
    public static ReportDialog showStreamable(@NotNull Project project,
                                              @NotNull String title,
                                              @NotNull String rawSource,
                                              @NotNull String initialMarkdown) {
        return ReportDialogs.showStreamable(project, title, rawSource, initialMarkdown);
    }
}
