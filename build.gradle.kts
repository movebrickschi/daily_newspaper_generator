plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.fighting.study"
version = "1.4.0"

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

    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
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

            <h3>1.3.0</h3>
            English:
            <ul>
                <li>Support any OpenAI-compatible LLM provider (Zhipu / DeepSeek / Qwen / Kimi / OpenAI / Ollama ...) by configuring Base URL, API Key and Model</li>
                <li>Today's commits now aggregate across all Git repositories registered under the IDEA project, grouped by repository and current branch</li>
            </ul>

            中文:
            <ul>
                <li>支持任意兼容 OpenAI 协议的大模型厂商（智谱 / DeepSeek / 通义千问 / Kimi / OpenAI / Ollama 等），在设置中配置 Base URL、API Key、Model 即可切换</li>
                <li>「今日提交记录 / 生成今日日报」支持 IDEA 项目下挂载的多个 Git 仓库，默认聚合所有仓库并按「仓库 → 当前分支」两级分组输出</li>
            </ul>
        """.trimIndent()
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
