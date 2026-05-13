package io.github.movebrickschi.dailynewspapergenerator.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    /** 单次 git log 进程超时（秒）。超时后强制 kill，避免巨型 monorepo 卡死 IDE。 */
    private static final long GIT_PROCESS_TIMEOUT_SEC = 60L;

    /** 多仓库并发抽取的最大并行度。超过此值后剩余仓库串行排队，避免一次性 fork 太多 git 进程。 */
    private static final int MAX_PARALLEL_REPOS = 8;

    /** 全仓库并发抽取的总等待上限（秒）。考虑 monorepo 也设个保险，防止 future 永久 hang。 */
    private static final long PARALLEL_ALL_TIMEOUT_SEC = 180L;

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
     * <p>
     * <b>实现路径</b>：
     * <ol>
     *   <li>主路径：通过 {@link Git#runCommand(GitLineHandler)} 调用 IDE 配置的 git
     *       可执行文件，复用 IDE 的认证 / 取消 / 进度机制；</li>
     *   <li>回退：当 IDE 未识别任何 Git 仓库时（如临时 sample 项目），降级使用
     *       {@link ProcessBuilder} 直接拼接 git 命令，避免完全失败。</li>
     * </ol>
     */
    public static Result extract(Project project, ExtractOptions options) {
        List<GitRepository> repos = GitRepositoryManager.getInstance(project).getRepositories();
        if (repos.isEmpty()) {
            String basePath = project.getBasePath();
            if (basePath == null) {
                throw new RuntimeException("无法获取项目路径");
            }
            return extractSingle(basePath, new File(basePath).getName(), null, options);
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(reportTitle(options)).append("\n\n");

        // 并发抽取：用 CompletableFuture + IDE shared pool，单仓库 IO 受限于 git 进程而非 CPU，
        // 在 monorepo（10+ 子仓库）场景下整体吞吐可显著提升。
        List<RepoOutcome> outcomes = parallelExtract(project, repos, options);

        int totalCommits = 0;
        int totalAdds = 0;
        int totalDels = 0;
        int repoWithCommits = 0;

        for (RepoOutcome out : outcomes) {
            if (out.error != null) {
                md.append("## ").append(out.repoName).append("\n\n_抽取失败：")
                        .append(out.error).append("_\n\n");
                continue;
            }
            if (out.result != null && out.result.commitCount() > 0) {
                md.append(out.result.markdown());
                totalCommits += out.result.commitCount();
                totalAdds += out.result.additions();
                totalDels += out.result.deletions();
                repoWithCommits++;
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

    /** 并发抽取单条结果。`result` 与 `error` 互斥。 */
    private static final class RepoOutcome {
        final String repoName;
        final Result result;
        final String error;
        RepoOutcome(String repoName, Result result, String error) {
            this.repoName = repoName;
            this.result = result;
            this.error = error;
        }
    }

    /**
     * 并发执行单仓库抽取并按 {@code repos} 原顺序回收结果。
     * <ul>
     *   <li>使用 IDE shared pool ({@link ApplicationManager#executeOnPooledThread})</li>
     *   <li>通过 {@link #MAX_PARALLEL_REPOS} 简单分批避免一次性 fork 几十个 git 进程</li>
     *   <li>每批结束在主线程检查一次 {@link ProgressManager#checkCanceled}</li>
     *   <li>总超时 {@link #PARALLEL_ALL_TIMEOUT_SEC} 秒，超时按 timeout 标记并退出循环</li>
     * </ul>
     */
    private static List<RepoOutcome> parallelExtract(@NotNull Project project,
                                                     @NotNull List<GitRepository> repos,
                                                     @NotNull ExtractOptions options) {
        List<RepoOutcome> all = new ArrayList<>(repos.size());
        for (int start = 0; start < repos.size(); start += MAX_PARALLEL_REPOS) {
            ProgressManager.checkCanceled();
            int end = Math.min(repos.size(), start + MAX_PARALLEL_REPOS);
            List<GitRepository> batch = repos.subList(start, end);
            List<CompletableFuture<RepoOutcome>> futures = new ArrayList<>(batch.size());
            for (GitRepository repo : batch) {
                futures.add(submitOne(project, repo, options));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(PARALLEL_ALL_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("并发抽取批次 [{}~{}) 超时或异常: {}", start, end, e.getMessage());
            }
            for (int i = 0; i < futures.size(); i++) {
                CompletableFuture<RepoOutcome> f = futures.get(i);
                String repoName = batch.get(i).getRoot().getName();
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    try {
                        all.add(f.getNow(null));
                        continue;
                    } catch (Exception ignored) {
                    }
                }
                all.add(new RepoOutcome(repoName, null, "执行超时或被取消"));
                f.cancel(true);
            }
        }
        return all;
    }

    private static CompletableFuture<RepoOutcome> submitOne(@NotNull Project project,
                                                            @NotNull GitRepository repo,
                                                            @NotNull ExtractOptions options) {
        CompletableFuture<RepoOutcome> fut = new CompletableFuture<>();
        String repoName = repo.getRoot().getName();
        String repoPath = repo.getRoot().getPath();
        String branch = repo.getCurrentBranchName();
        if (branch == null) {
            branch = "HEAD (detached)";
        }
        final String branchFinal = branch;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Result single = extractSingleByGit4Idea(project, repo, branchFinal, options);
                fut.complete(new RepoOutcome(repoName, single, null));
            } catch (Throwable t) {
                log.warn("抽取 {} 失败: {}", repoPath, t.getMessage());
                fut.complete(new RepoOutcome(repoName, null, t.getMessage()));
            }
        });
        return fut;
    }

    /**
     * Git4Idea 路径：通过 {@link Git#runCommand(GitLineHandler)} 调用 IDE 配置的
     * git executable，复用 IDE 的认证、取消信号、超时控制。
     */
    private static Result extractSingleByGit4Idea(@NotNull Project project,
                                                  @NotNull GitRepository repo,
                                                  String branch,
                                                  ExtractOptions options) {
        VirtualFile root = repo.getRoot();
        List<String> authors = effectiveAuthorsByGit4Idea(project, root, options.authors);
        List<RawCommit> commits = runLogByGit4Idea(project, root, authors, options);
        return renderCommits(commits, root.getName(), branch, options);
    }

    /** ProcessBuilder fallback：仅当 IDE 未识别 Git 仓库时使用。 */
    private static Result extractSingle(String repoPath, String repoName, String branch, ExtractOptions options) {
        List<String> authors = effectiveAuthors(repoPath, options.authors);
        List<RawCommit> commits = runLog(repoPath, authors, options);
        return renderCommits(commits, repoName, branch, options);
    }

    /** 共用渲染逻辑，避免两条 extract 路径里重复拼装 markdown。 */
    private static Result renderCommits(List<RawCommit> commits, String repoName, String branch, ExtractOptions options) {
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
        List<String> out = new ArrayList<>();
        try {
            String userName = readGitConfig(repoPath, "user.name");
            if (userName != null && !userName.isBlank()) {
                out.add(userName);
            }
        } catch (Exception e) {
            log.warn("读取 git user.name 失败: {}", e.getMessage());
        }
        try {
            String userEmail = readGitConfig(repoPath, "user.email");
            if (userEmail != null && !userEmail.isBlank() && !out.contains(userEmail)) {
                out.add(userEmail);
            }
        } catch (Exception e) {
            log.warn("读取 git user.email 失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 通过 IDE 的 {@link GitConfigUtil} 读取 git 配置，避免在外部进程上单独 fork
     * 一次 {@code git config}。读取失败时返回空列表，由调用方决定后续行为。
     */
    private static List<String> effectiveAuthorsByGit4Idea(@NotNull Project project,
                                                           @NotNull VirtualFile root,
                                                           List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        List<String> out = new ArrayList<>();
        try {
            String userName = GitConfigUtil.getValue(project, root, "user.name");
            if (userName != null && !userName.isBlank()) {
                out.add(userName);
            }
        } catch (Exception e) {
            log.warn("Git4Idea 读取 user.name 失败 [{}]: {}", root.getPath(), e.getMessage());
        }
        try {
            String userEmail = GitConfigUtil.getValue(project, root, "user.email");
            if (userEmail != null && !userEmail.isBlank() && !out.contains(userEmail)) {
                out.add(userEmail);
            }
        } catch (Exception e) {
            log.warn("Git4Idea 读取 user.email 失败 [{}]: {}", root.getPath(), e.getMessage());
        }
        return out;
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

    /**
     * Git4Idea 路径下的 {@code git log} 执行：
     * <ul>
     *   <li>用 {@link GitLineHandler} 构造命令，受 IDE 的 git 路径、认证、超时管控；</li>
     *   <li>用 {@link Git#runCommand(GitLineHandler)} 同步执行，自动响应当前 {@link ProgressManager} 的取消；</li>
     *   <li>{@code GitLineHandler} 内部按 {@code \n} 切分输出；本方法把行 list 用
     *       {@code \n} 重新 join，再交给 {@link #parseLog} 以 {@code 0x1e/0x1f} 协议解析。</li>
     * </ul>
     */
    private static List<RawCommit> runLogByGit4Idea(@NotNull Project project,
                                                    @NotNull VirtualFile root,
                                                    @NotNull List<String> authors,
                                                    @NotNull ExtractOptions options) {
        GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LOG);

        ExtractOptions.DateBounds bounds = options.computeBounds();
        handler.addParameters("--since=" + bounds.since().format(ISO) + " 00:00:00");
        handler.addParameters("--until=" + bounds.until().format(ISO) + " 23:59:59");
        if (options.skipMerges) {
            handler.addParameters("--no-merges");
        }
        for (String a : authors) {
            handler.addParameters("--author=" + a);
        }
        // 记录间 0x1e、字段间 0x1f；详见 parseLog 文档。
        handler.addParameters("--pretty=format:%H%x1f%an%x1f%ai%x1f%s%x1f%b%x1e");
        if (options.includeStats) {
            handler.addParameters("--shortstat");
        }

        GitCommandResult result;
        try {
            result = Git.getInstance().runCommand(handler);
        } catch (Throwable t) {
            throw new RuntimeException("git log 执行失败 [" + root.getPath() + "]: " + t.getMessage(), t);
        }
        if (result == null) {
            throw new RuntimeException("git log 无响应 [" + root.getPath() + "]");
        }
        if (!result.success()) {
            String err = result.getErrorOutputAsJoinedString();
            if (err == null || err.isBlank()) {
                err = result.getOutputAsJoinedString();
            }
            throw new RuntimeException("git log 失败 [" + root.getPath() + "] exit="
                    + result.getExitCode() + ": " + truncate(err, 400));
        }
        // 把按行切的 output 用 \n 重新拼回去；commit body 内本身的 \n 也会被还原。
        String raw = String.join("\n", result.getOutput());
        return parseLog(raw, options);
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
        // 多 author OR 行为：git log 多 --author 默认是 OR。
        // 记录间使用 ASCII RECORD SEPARATOR (0x1e)，字段间使用 UNIT SEPARATOR (0x1f)；
        // 两个控制字符在 commit message 中几乎不可能出现，且不会被 Git4Idea 的 line handler
        // 按 \n 切分时拆散。
        cmd.add("--pretty=format:%H%x1f%an%x1f%ai%x1f%s%x1f%b%x1e");
        if (options.includeStats) {
            cmd.add("--shortstat");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(repoPath));
            pb.environment().putIfAbsent("LC_ALL", "C");
            Process p = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outReader = pipeReader(p.getInputStream(), stdout,
                    "git-log-stdout-" + new File(repoPath).getName());
            Thread errReader = pipeReader(p.getErrorStream(), stderr,
                    "git-log-stderr-" + new File(repoPath).getName());

            boolean finished = p.waitFor(GIT_PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("git log 超时 [" + repoPath + "] > " + GIT_PROCESS_TIMEOUT_SEC + "s，已强制终止");
            }
            outReader.join(2000);
            errReader.join(2000);

            int exit = p.exitValue();
            if (exit != 0) {
                String hint = stderr.length() > 0 ? stderr.toString() : stdout.toString();
                throw new RuntimeException("git log 失败 [" + repoPath + "] exit=" + exit + ": " + truncate(hint, 400));
            }
            if (stderr.length() > 0) {
                log.debug("git log stderr [{}]: {}", repoPath, truncate(stderr.toString(), 200));
            }
            return parseLog(stdout.toString(), options);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("git log 被中断 [" + repoPath + "]", ie);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Thread pipeReader(java.io.InputStream is, StringBuilder sink, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (Exception ignored) {
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 解析 git log 输出。
     * <p>
     * 记录间以 {@code %x1e}（ASCII RECORD SEPARATOR, 0x1e）分隔；
     * 记录内字段以 {@code %x1f}（UNIT SEPARATOR, 0x1f）分隔：
     * {@code hash, author, isoTime, subject, body}。
     * <p>
     * 若开启了 {@code --shortstat}，每个 record 的 body 之后可能附带形如
     * {@code "\n 3 files changed, 12 insertions(+), 5 deletions(-)\n"} 的 stat 行，
     * 解析时会从 tail 中识别并提取 additions/deletions，剩余视为 body。
     */
    // 包私有方便同包单元测试直接驱动，无需反射。
    static List<RawCommit> parseLog(String raw, ExtractOptions options) {
        List<RawCommit> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return list;
        }
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

    // 包私有：让同包单测可读断言；线上不暴露 getter 避免被外部依赖。
    static final class RawCommit {
        String hash;
        String author;
        String time;
        String subject;
        String body;
        int additions;
        int deletions;

        String getHash() { return hash; }
        String getAuthor() { return author; }
        String getSubject() { return subject; }
        String getBody() { return body; }
        int getAdditions() { return additions; }
        int getDeletions() { return deletions; }
    }

    /** ISO-aligned bounds. Today is included in "this week". */
    public static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() + 6) % 7);
    }

    public static DayOfWeek mondayConst() {
        return DayOfWeek.MONDAY;
    }
}
