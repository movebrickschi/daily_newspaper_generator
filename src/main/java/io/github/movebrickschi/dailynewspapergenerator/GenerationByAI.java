package io.github.movebrickschi.dailynewspapergenerator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

public class GenerationByAI extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;


        // 生成日报内容
        String dailyReport = Generation.getSelectedCommits(e);

        dailyReport = ZhipuUtil.polish(dailyReport);

        // 显示结果
        Generation.showReportInDialog(project, dailyReport);
    }


}
