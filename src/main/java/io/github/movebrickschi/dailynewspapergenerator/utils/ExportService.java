package io.github.movebrickschi.dailynewspapergenerator.utils;

import com.intellij.openapi.project.Project;
import io.github.movebrickschi.dailynewspapergenerator.config.LlmSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Markdown 导出服务。默认目录：项目根/docs/daily/yyyy-MM-dd.md。
 *
 * @author Liu Chunchi
 */
public final class ExportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExportService() {
    }

    public static File exportMarkdown(@NotNull Project project, @NotNull String content, @NotNull String title) throws IOException {
        File dir = resolveOutputDir(project);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }
        String dateStr = LocalDate.now().format(ISO);
        String safeTitle = sanitize(title);
        File target = new File(dir, dateStr + (safeTitle.isEmpty() ? "" : "-" + safeTitle) + ".md");
        int counter = 1;
        while (target.exists()) {
            target = new File(dir, dateStr + (safeTitle.isEmpty() ? "" : "-" + safeTitle) + "-" + counter + ".md");
            counter++;
        }
        Files.writeString(target.toPath(), content == null ? "" : content, StandardCharsets.UTF_8);
        return target;
    }

    private static File resolveOutputDir(Project project) {
        LlmSettings settings = LlmSettings.getInstance();
        if (settings != null && settings.outputDir != null && !settings.outputDir.isBlank()) {
            File f = new File(settings.outputDir);
            if (f.isAbsolute()) {
                return f;
            }
            String basePath = project.getBasePath();
            return basePath == null ? f : new File(basePath, settings.outputDir);
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            return new File(System.getProperty("user.home"), "docs/daily");
        }
        return new File(basePath, "docs/daily");
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        String t = s.trim();
        // 把不允许的文件名字符替换掉
        t = t.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (t.length() > 40) {
            t = t.substring(0, 40);
        }
        return t;
    }
}
