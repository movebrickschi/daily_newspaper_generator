package io.github.movebrickschi.dailynewspapergenerator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.ui.ReportDialogs;
import io.github.movebrickschi.dailynewspapergenerator.utils.ExtractOptions;
import io.github.movebrickschi.dailynewspapergenerator.utils.GitCommitExtractor;
import io.github.movebrickschi.dailynewspapergenerator.utils.ReportActionRunner;
import org.jetbrains.annotations.NotNull;

/**
 * 提取当天当前用户的所有提交记录。
 *
 * @author Liu Chunchi
 */
public class ExtractTodayCommitsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ReportActionRunner.runInBackground(project, "正在提取今日提交...", "提取失败", indicator -> {
            indicator.setText("正在抽取，请稍候...");
            String md = getTodaysCommits(project);
            ApplicationManager.getApplication().invokeLater(() ->
                    ReportDialogs.show(project, "今日提交记录", md));
        });
    }

    /**
     * 给 {@link GenerationTodayCommitsByAIAction} 用的便捷方法：
     * 直接以「今天 + 当前用户 + 默认过滤」为参数走 {@link GitCommitExtractor}。
     */
    public static String getTodaysCommits(Project project) {
        ExtractOptions opt = ExtractOptions.today();
        return GitCommitExtractor.extract(project, opt).markdown();
    }
}
