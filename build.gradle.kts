plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.fighting.study"
version = "1.5.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        // 调试的idea 版本
        create("IC", "2025.2.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("Git4Idea")
    }

    // SLF4J 由 IntelliJ Platform 提供运行时实现（slf4j-api + 内置 binding）；
    // 插件只在编译期需要 API，不应再打包 slf4j-impl，否则会与 platform 冲突产生
    // "Class path contains multiple SLF4J bindings" 警告。
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        // 设置插件的兼容版本,开始版本
        ideaVersion {
            sinceBuild = "242"
        }
        // 设置插件的变更说明
        changeNotes = """
            <h3>1.5.0</h3>
            English:
            <ul>
                <li><b>Git integration via Git4Idea API</b>: reuses IDE-configured git path / auth / cancellation</li>
                <li><b>Multi-repo parallel extraction</b>: 8-way concurrent extraction for monorepo, with cancellable progress</li>
                <li><b>HTTP/2 with HTTP/1.1 fallback</b> for LLM calls; configurable timeout via JVM system properties</li>
                <li><b>Polish result cache</b> (sha256 + LRU): repeated repolishes of same content skip the LLM call</li>
                <li><b>Hardened credentials</b>: API Key no longer drifts in heap; test-connection no longer silently writes to PasswordSafe; AccessTokenCache hashes secrets</li>
                <li><b>Better Feishu rendering</b>: blockquote / hr / h3+ adapted to Feishu interactive card markdown subset</li>
                <li><b>Copy as HTML + Markdown</b>: clipboard exposes both flavors for Word / Outlook / browser editors</li>
                <li><b>Export filename adds minute timestamp</b>; settings changes auto-refresh open report dialogs via MessageBus</li>
                <li><b>ReportDialog refactor</b>: extracted ReportButtonFactory / ReportPushHelper; new ReportDialogs entry (V2 deprecated)</li>
                <li><b>MarkdownEngine plug point</b> for future commonmark / flexmark / IntelliJ markdown backend</li>
                <li><b>CI workflow</b> + plugin verifier configuration; 21 new JUnit tests</li>
            </ul>

            中文:
            <ul>
                <li><b>Git 集成升级到 IntelliJ Git4Idea API</b>：复用 IDE 配置的 git 路径、认证与取消机制</li>
                <li><b>多仓库并发抽取</b>：monorepo 抽取改为 8 路并发，受 IDE 进度条接管可随时取消</li>
                <li><b>LLM 请求 HTTP/2 + HTTP/1.1 自动降级</b>；超时可通过 JVM 系统属性配置</li>
                <li><b>润色结果缓存</b>（sha256 + LRU）：相同 model + prompt + content 命中即跳过 LLM 调用，节省 token</li>
                <li><b>凭证安全加固</b>：API Key 不再在堆中长期漂浮；测试连接不再悄悄把临时输入写入 PasswordSafe；AccessTokenCache 用 sha256 哈希 secret 作 key</li>
                <li><b>飞书 markdown 适配</b>：自动把 blockquote / hr / h3+ 转换为飞书 interactive 卡片支持的子集</li>
                <li><b>复制即同时塞 markdown + HTML</b>：在 Word / Outlook / 浏览器编辑器中粘贴自动得到富文本</li>
                <li><b>导出文件名加分钟级时间戳</b>；设置保存后已打开的报告对话框通过 MessageBus 自动刷新</li>
                <li><b>ReportDialog 拆分</b>：抽出 ReportButtonFactory / ReportPushHelper；新入口 ReportDialogs（V2 deprecated）</li>
                <li><b>MarkdownEngine 抽象</b>，未来可接入 commonmark / flexmark / IntelliJ 自带 markdown</li>
                <li><b>新增 GitHub Actions CI</b> + plugin verifier 配置；21 个新 JUnit 测试</li>
            </ul>

            <h3>1.4.0</h3>
            English:
            <ul>
                <li><b>Streaming LLM output</b>: chat completions now stream via SSE, rendered incrementally in the result dialog with real-time cancel support</li>
                <li><b>Secure credentials</b>: API Key is stored in IntelliJ PasswordSafe with automatic migration from legacy plain-text settings; API Key field becomes a password field with show/hide toggle</li>
                <li><b>Test Connection</b>: one-click endpoint probe in settings with clear success/timeout/auth error feedback</li>
                <li><b>Extract by range</b>: new action "Extract By Range…" (Ctrl+Alt+B) with date range presets (today / yesterday / this week / last week / last 7 days / custom), multi-author filter, and merge / revert / WIP skip options across all project repositories</li>
                <li><b>Conventional Commits grouping</b> & commit/line-diff statistics appended to the report (toggleable)</li>
                <li><b>Redesigned result dialog</b>: editable Markdown area with live preview tab, "Copy / Export .md / Push / Repolish / Close" actions, and streaming progress in the title</li>
                <li><b>One-click push</b> to Feishu / DingTalk Robot / WeChat Work / Generic Webhook / <b>DingTalk Daily-Report Template</b> (OAPI report/create with cached access_token)</li>
                <li><b>Multi-channel & multi-template management</b>: add/edit/remove prompt templates and delivery channels in settings with dedicated list panels</li>
                <li><b>Export to .md</b> with a configurable default path (project/docs/daily/yyyy-MM-dd.md), auto dedupe and open-in-IDE</li>
                <li><b>i18n bundle</b> for all UI strings (zh_CN default + en_US)</li>
                <li><b>Menu polish</b>: keyboard shortcuts now shown in menu items; error notifications carry "Open Settings" and retry actions</li>
            </ul>

            中文:
            <ul>
                <li><b>流式输出</b>：LLM 调用改走 SSE，结果对话框边接收边渲染，支持实时取消</li>
                <li><b>凭证安全</b>：API Key 改用 IntelliJ PasswordSafe 加密存储，自动迁移旧明文配置；设置页使用密码框 + 显示/隐藏切换</li>
                <li><b>测试连接</b>：设置页一键探测 LLM 端点，明确反馈成功 / 超时 / 鉴权错误</li>
                <li><b>按范围提取</b>：新增"按范围提取提交…"（Ctrl+Alt+B），支持日期预设（今天 / 昨天 / 本周 / 上周 / 最近 7 天 / 自定义）、多作者过滤、跳过 merge/revert/WIP，自动聚合项目下所有 Git 仓库</li>
                <li>报告末尾按 <b>Conventional Commits</b> 前缀分组（feat/fix/docs/...），附带 commit 数与 --shortstat 增删行统计（可开关）</li>
                <li><b>结果对话框重构</b>：可编辑 Markdown + 预览 Tab，底部提供「复制 / 导出 md / 推送 / 再润色 / 关闭」，流式生成进度显示在标题栏</li>
                <li><b>一键推送</b>：飞书 / 钉钉机器人 / 企业微信 / 通用 Webhook / <b>钉钉日志模板</b>（OAPI report/create + access_token 缓存）</li>
                <li><b>多通道 & 多提示词模板</b>：设置页独立面板管理多套提示词模板和推送通道，可增删改</li>
                <li><b>导出 .md</b>：默认输出到 项目/docs/daily/yyyy-MM-dd.md（可自定义），自动避重命名并在 IDE 打开</li>
                <li><b>国际化</b>：所有 UI 文案抽到资源包（中文默认 + 英文）</li>
                <li><b>菜单优化</b>：菜单项后附快捷键展示；错误通知自带「打开设置」与重试入口</li>
            </ul>

            <p style="margin-top:12px;font-size:11px;color:#888;">
                Older release notes (1.3.0 and earlier) are available in the project's CHANGELOG.md.
            </p>
        """.trimIndent()
    }

    // 插件兼容性 verifier：CI 配套使用，提前发现使用了 since-build 之后版本中
    // 已 deprecated / removed 的 API。
    //
    // 显式锁定 since-build 起点 + 当前调试版本两个 IDE release，避免 recommended() 抓到
    // 仓库里尚未发布的 EAP 而失败。
    //
    // failureLevel 仅在结构性问题（INVALID_PLUGIN）时 fail —— COMPATIBILITY_PROBLEMS
    // 经常因网络无法解析其它 plugin 依赖（plugins.jetbrains.com 不可达）而误报，
    // 通过 report 文件人工审阅即可。
    pluginVerification {
        ides {
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.0.2")
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.2")
        }
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        )
    }
}

tasks {
    // 设置 JVM 兼容性版本
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
