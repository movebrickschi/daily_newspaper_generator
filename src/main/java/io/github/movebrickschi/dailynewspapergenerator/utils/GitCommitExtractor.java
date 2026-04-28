package io.github.movebrickschi.dailynewspapergenerator.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 提交记录抽取器：根据 {@link ExtractOptions} 从项目下所有仓库聚合提交，
 * 可选 Conventional Commits 分组与代码增删行统计。
 *
 * @author Liu Chunchi
 */
public final class GitCommitExtractor {

    private static final Logger log = LoggerFactory.getLogger(GitCommitExtractor.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /** Conventional Commits 头部正则： {@code type(scope): subject} 或 {@code type: subject}。 */
    private static final Pattern CC_PATTERN = Pattern.compile(
            "^(?<type>feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)" +
                    "(?:\\([^)]*\\))?!?:\\s*(?<subject>.+)",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> CC_ORDER = List.of(
            "feat", "fix", "perf", "refactor", "test", "docs", "style", "build", "ci", "chore", "revert", "other"
    );

    private GitCommitExtractor() {
    }

    /** 一次抽取的输出。 */
    public record Result(String markdown, int commitCount, int repoCount, int additions, int deletions) {
    }

    /**
     * 主入口：在所有 Git4Idea 识别的仓库下按 options 抽取提交，并聚合输出 markdown。
     */
    public static Result extract(Project project, ExtractOptions options) {
        List<GitRepository> repos = GitRepositoryManager.getInstance(project).getRepositories();
        if (repos.isEmpty()) {
            // 回退：把项目根当作单仓库
            String basePath = project.getBasePath();
            if (basePath == null) {
                throw new RuntimeException("无法获取项目路径");
            }
            return extractSingle(basePath, new File(basePath).getName(), null, options);
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(reportTitle(options)).append("\n\n");

        int totalCommits = 0;
        int totalAdds = 0;
        int totalDels = 0;
        int repoWithCommits = 0;

        for (GitRepository repo : repos) {
            VirtualFile rootVf = repo.getRoot();
            String repoPath = rootVf.getPath();
            String repoName = rootVf.getName();
            String branch = repo.getCurrentBranchName();
            if (branch == null) {
                branch = "HEAD (detached)";
            }
            try {
                Result single = extractSingle(repoPath, repoName, branch, options);
                if (single.commitCount() > 0) {
                    md.append(single.markdown());
                    totalCommits += single.commitCount();
                    totalAdds += single.additions();
                    totalDels += single.deletions();
                    repoWithCommits++;
                }
            } catch (Exception e) {
                log.warn("抽取 {} 失败: {}", repoPath, e.getMessage());
                md.append("## ").append(repoName).append("\n\n_抽取失败：")
                        .append(e.getMessage()).append("_\n\n");
            }
        }

        if (totalCommits == 0) {
            return new Result(reportTitle(options) + "\n\n_所选范围内无提交_\n", 0, 0, 0, 0);
        }

        if (options.includeStats) {
            md.append("---\n");
            md.append("**统计**：共 ").append(totalCommits).append(" 个 commit，跨 ")
                    .append(repoWithCommits).append(" 个仓库");
            if (totalAdds > 0 || totalDels > 0) {
                md.append("，代码变更 +").append(totalAdds).append(" / -").append(totalDels).append(" 行");
            }
            md.append("。\n");
        }

        return new Result(md.toString(), totalCommits, repoWithCommits, totalAdds, totalDels);
    }

    private static Result extractSingle(String repoPath, String repoName, String branch, ExtractOptions options) {
        List<String> authors = effectiveAuthors(repoPath, options.authors);
        List<RawCommit> commits = runLog(repoPath, authors, options);

        if (commits.isEmpty()) {
            return new Result("", 0, 0, 0, 0);
        }

        StringBuilder md = new StringBuilder();
        md.append("## ").append(repoName).append("\n");
        if (branch != null) {
            md.append("### 分支：").append(branch).append("\n");
        }
        md.append("\n");

        int addAll = 0;
        int delAll = 0;
        for (RawCommit c : commits) {
            addAll += c.additions;
            delAll += c.deletions;
        }

        if (options.classifyByConventional) {
            Map<String, List<RawCommit>> grouped = groupByCC(commits);
            for (String type : CC_ORDER) {
                List<RawCommit> g = grouped.get(type);
                if (g == null || g.isEmpty()) {
                    continue;
                }
                md.append("**").append(headingForType(type)).append("** (").append(g.size()).append(")\n\n");
                for (RawCommit c : g) {
                    appendBullet(md, c);
                }
                md.append("\n");
            }
        } else {
            for (RawCommit c : commits) {
                appendBullet(md, c);
            }
            md.append("\n");
        }

        return new Result(md.toString(), commits.size(), 1, addAll, delAll);
    }

    private static void appendBullet(StringBuilder md, RawCommit c) {
        md.append("- ").append(c.subject == null || c.subject.isBlank() ? "(无主题)" : c.subject.trim());
        if (c.body != null && !c.body.isBlank()) {
            String[] bodyLines = c.body.trim().split("\\r?\\n");
            for (String line : bodyLines) {
                md.append("\n  ").append(line);
            }
        }
        md.append("\n");
    }

    private static String headingForType(String type) {
        return switch (type) {
            case "feat" -> "✨ 新功能";
            case "fix" -> "🐛 修复";
            case "perf" -> "⚡ 性能";
            case "refactor" -> "♻️ 重构";
            case "test" -> "✅ 测试";
            case "docs" -> "📝 文档";
            case "style" -> "💄 样式";
            case "build" -> "📦 构建";
            case "ci" -> "🔧 CI";
            case "chore" -> "🔨 杂项";
            case "revert" -> "⏪ Revert";
            default -> "其它";
        };
    }

    private static Map<String, List<RawCommit>> groupByCC(List<RawCommit> commits) {
        Map<String, List<RawCommit>> map = new LinkedHashMap<>();
        for (String t : CC_ORDER) {
            map.put(t, new ArrayList<>());
        }
        for (RawCommit c : commits) {
            String subject = c.subject == null ? "" : c.subject;
            Matcher m = CC_PATTERN.matcher(subject.trim());
            String type;
            if (m.find()) {
                type = m.group("type").toLowerCase(Locale.ROOT);
                c.subject = m.group("subject");
            } else {
                type = "other";
            }
            map.get(type).add(c);
        }
        return map;
    }

    private static List<String> effectiveAuthors(String repoPath, List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        try {
            String userName = readGitConfig(repoPath, "user.name");
            if (userName != null && !userName.isBlank()) {
                List<String> out = new ArrayList<>();
                out.add(userName);
                return out;
            }
        } catch (Exception e) {
            log.warn("读取 git user.name 失败: {}", e.getMessage());
        }
        return List.of();
    }

    private static String readGitConfig(String repoPath, String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "config", key);
        pb.directory(new File(repoPath));
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        String line = r.readLine();
        p.waitFor();
        return line == null ? null : line.trim();
    }

    private static List<RawCommit> runLog(String repoPath, List<String> authors, ExtractOptions options) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("log");

        // 时间范围
        ExtractOptions.DateBounds bounds = options.computeBounds();
        cmd.add("--since=" + bounds.since().format(ISO) + " 00:00:00");
        cmd.add("--until=" + bounds.until().format(ISO) + " 23:59:59");

        if (options.skipMerges) {
            cmd.add("--no-merges");
        }

        for (String a : authors) {
            cmd.add("--author=" + a);
        }
        // 多 author OR 行为：git log 多 --author 默认是 OR
        cmd.add("--pretty=format:%H%x1f%an%x1f%ai%x1f%s%x1f%b%x1e");
        if (options.includeStats) {
            cmd.add("--shortstat");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(false);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                StringBuilder err = new StringBuilder();
                try (BufferedReader er = new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = er.readLine()) != null) {
                        err.append(line).append('\n');
                    }
                }
                throw new RuntimeException("git log 失败 [" + repoPath + "]: " + err);
            }
            return parseLog(out.toString(), options);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 解析输出。每条记录以 0x1e 分隔，记录内字段以 0x1f 分隔：hash、author、isoTime、subject、body。
     * 如开启了 --shortstat，下一条记录前可能会出现一行类似:
     * "  3 files changed, 12 insertions(+), 5 deletions(-)"
     */
    private static List<RawCommit> parseLog(String raw, ExtractOptions options) {
        List<RawCommit> list = new ArrayList<>();
        String[] records = raw.split("\u001e");
        Pattern statPat = Pattern.compile("(\\d+)\\s+insertion[s]?\\(\\+\\)|(\\d+)\\s+deletion[s]?\\(-\\)");
        for (String chunk : records) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) continue;
            String[] fields = trimmed.split("\u001f", 5);
            if (fields.length < 4) continue;
            RawCommit c = new RawCommit();
            c.hash = fields[0];
            c.author = fields[1];
            c.time = fields[2];
            c.subject = fields[3];
            String tail = fields.length >= 5 ? fields[4] : "";
            // tail 可能包含 body + 换行 + shortstat 行
            String body = tail;
            if (options.includeStats) {
                String[] tlines = tail.split("\\r?\\n");
                StringBuilder bodyBuf = new StringBuilder();
                for (String tline : tlines) {
                    Matcher mm = statPat.matcher(tline);
                    boolean matched = false;
                    while (mm.find()) {
                        matched = true;
                        if (mm.group(1) != null) {
                            c.additions += parseIntSafe(mm.group(1));
                        }
                        if (mm.group(2) != null) {
                            c.deletions += parseIntSafe(mm.group(2));
                        }
                    }
                    if (!matched && !tline.isBlank()) {
                        bodyBuf.append(tline).append('\n');
                    }
                }
                body = bodyBuf.toString().trim();
            }
            c.body = body;
            // 跳过 revert/wip
            if (options.skipReverts && c.subject != null && c.subject.toLowerCase(Locale.ROOT).startsWith("revert")) {
                continue;
            }
            if (options.skipWip && c.subject != null
                    && (c.subject.toLowerCase(Locale.ROOT).startsWith("wip") || c.subject.toLowerCase(Locale.ROOT).contains("[wip]"))) {
                continue;
            }
            list.add(c);
        }
        return list;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String reportTitle(ExtractOptions options) {
        ExtractOptions.DateBounds b = options.computeBounds();
        String since = b.since().format(ISO);
        String until = b.until().format(ISO);
        if (since.equals(until)) {
            return "提交日报 " + since;
        }
        return "提交日报 " + since + " ~ " + until;
    }

    static final class RawCommit {
        String hash;
        String author;
        String time;
        String subject;
        String body;
        int additions;
        int deletions;
    }

    /** ISO-aligned bounds. Today is included in "this week". */
    public static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() + 6) % 7);
    }

    public static DayOfWeek mondayConst() {
        return DayOfWeek.MONDAY;
    }
}
