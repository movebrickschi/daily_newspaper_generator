# Changelog

本文档记录 `daily_newspaper_generator` 插件每个发布版本的显著变更，格式遵循
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与
[语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.5.0] - Unreleased

> 体系级稳定性 / 安全性 / 架构梳理。本版本完成了 40 项审查项中的 34 项，
> 详见 P0 / P1 / P2 三档分类。

### Security & Correctness（P0）

- **Git 集成升级到 IntelliJ Git4Idea API**（`GitLineHandler` + `Git.getInstance().runCommand`），
  复用 IDE 配置的 git 路径、认证与取消机制；保留裸 `ProcessBuilder` 作为降级路径。
- **作者过滤同时识别 `user.name` 与 `user.email`**，避免重名误抓。
- **修复 `SecureKeyStore.loadApiKey` 副作用**：测试连接时不再悄悄把临时输入的 API Key
  写入 PasswordSafe；自动迁移逻辑移到 `LlmSettings.scheduleMigrationIfNeeded` 异步执行。
- **`LlmUtil.effectiveSettings` 不再复制明文 apiKey 到 heap**：消除凭证在 GC 前的长期漂浮。
- **`AccessTokenCache` 改为 LRU + `sha256(appKey|secret)`** 哈希作 cache key：
  防止明文 secret 残留在 Map 中，并对老 AppKey 自动淘汰。
- **`HttpClient` 注入独立 `CookieManager(ACCEPT_NONE)`**：防止 LLM / 推送请求继承
  JVM 默认 cookie 而泄漏到非预期主机。
- **`git log` 子进程 stderr / stdout 分流**：避免 git warning 污染解析。
- **流式取消改用共享 `ScheduledExecutorService`**：消除每次请求都 fork 线程 + `Thread.sleep(50)`
  轮询的 CPU 浪费，仍可实时响应用户取消。

### Performance & Architecture（P1）

- **多 Git 仓库并发抽取**：单次 monorepo 调用从串行 → 8 路并发，整体抽取耗时随仓库数显著下降；
  受 `ProgressManager.checkCanceled` 接管，IDE 取消立即生效。
- **HTTP/2 自动降级 HTTP/1.1**：LLM 请求遇到 H2 协议错误 / GOAWAY 时自动 fallback 重试。
- **`HttpUtil` 超时可配化**：通过 JVM 系统属性 `dailyreport.http.timeout` /
  `dailyreport.http.connectTimeout` 覆盖默认 15s / 5s。
- **`PolishCache` LRU 缓存**：相同 model + prompt + content 命中即跳过 LLM 调用，
  节省 token；仅对非流式 `polish()` 生效。
- **流式期间跳过预览渲染**：高频 SSE delta 不再每 200ms 全量 reparse + setText，
  流式结束后统一渲染一次。
- **`ReportDialog` 上帝类拆分**：抽出 `ReportButtonFactory` / `ReportPushHelper`，
  主类从 462 → 367 行。
- **报告对话框命名澄清**：`ReportDialogV2` 标 `@Deprecated`，
  新代码改用 `ReportDialogs`（V2 向后兼容转发）。
- **4 个后台 Action 模板化**：抽出 `ReportActionRunner.runInBackground`，
  统一 `Task.Backgroundable` + try/catch + `NotifyUtil.error` 套路。
- **5 个 Channel Sender 共用 `SenderSupport`**：消除 `truncate` / `emptyToDefault`
  五处重复实现。
- **`LlmSettingsListener` (MessageBus topic)**：用户在设置页保存后，
  已打开的报告对话框自动刷新模板 / 通道下拉。
- **`MarkdownEngine` 抽象点**：保留 `MarkdownRenderer` 为默认实现，
  未来可换 `commonmark-java` / `flexmark` / IntelliJ 自带 markdown 引擎。
- **`LlmClient` 请求体改用 `ChatRequest` / `ChatMessage` record**：
  替代 `Map<String, Object>` 拼接，便于后续扩展 `temperature` / `max_tokens` 等字段。
- **`NotifyUtil.error` 支持自定义 actions 重载**：调用方可追加「重试 / 查看日志」入口。
- **`LlmSettings.scheduleMigrationIfNeeded`**：旧明文 API Key 迁移延迟到 BGT，
  IDE 启动不再被 PasswordSafe 解锁阻塞。

### UX & Polish（P2）

- **复制即同时塞 markdown + HTML**：在 Word / Outlook / 浏览器编辑器中粘贴自动得到富文本。
- **飞书 markdown 适配**：`FeishuRobotSender` 自动把 `> quote` / `---` / `###~######`
  转换为飞书 interactive 卡片支持的子集。
- **导出文件名加分钟级时间戳**：同一天多次导出不再被 `-1/-2/-N` 覆盖名。
- **设置页 displayName 走 i18n bundle**：`plugin.xml` 与 `LlmSettingsConfigurable.getDisplayName`
  统一从 `messages.DailyReportBundle.settings.title` 取，消除硬编码。
- **README 错别字修复**：`项目阶段性趍趍报告` → `进度报告`；`手动联联联` → `手动逐人统计`；
  `釘钉` → `钉钉`。

### Engineering（P2）

- **GitHub Actions CI**：`.github/workflows/build.yml` 跑 `build` + `verifyPlugin` +
  上传 `build/distributions/*.zip` artifact，含 gradle cache。
- **Plugin Verifier 配置**：`intellijPlatform.pluginVerification` 启用 `recommended()` IDE 矩阵，
  发现 `COMPATIBILITY_PROBLEMS` 失败。
- **`slf4j-simple` 改为 `testRuntimeOnly`，`slf4j-api` 改为 `compileOnly`**：
  避免与 IntelliJ Platform 自带 slf4j binding 冲突。
- **新增 21 个 JUnit 测试**：
  - `GitCommitExtractorParseLogTest`：8 个 case，覆盖 0x1e/0x1f 解析、shortstat、CC 分组等
  - `MarkdownRendererTest`：9 个 case，覆盖 XSS escape、`javascript:` 链接、表格等
  - `AccessTokenCacheTest`：4 个 case，覆盖 cacheKey 哈希质量与 invalidate null-safety

### Deferred / Cancelled

下列项审查时识别但本次未实现，已记录到 issue 排期：

- `P1-20` LlmProtocolAdapter（Claude / Gemini 支持）—— `ChatRequest` record 已预留扩展点
- `P2-30` commit message 中工单号自动超链 —— 需 settings 中新增仓库 base URL
- `P2-28` AuthorAutoCompleteField 邮箱补全 —— 需 git log 抽取唯一邮箱列表
- `P2-29` 抽取进度条显示已抓取 commit 数 —— 需 `ProgressIndicator` 参数贯穿
- `P2-31` token 上下文上限 / 分批润色 —— 需 token 估算 + 分批合并策略
- `P2-38` Gradle Version Catalog —— 当前依赖数少，收益 ≤ 维护成本

---

## [1.4.0] - 2025

英文 / 中文功能说明详见插件市场页面与 `build.gradle.kts` 中 `changeNotes`。

主要包含：流式 SSE / PasswordSafe 凭证迁移 / 一键测试连接 / 按范围提取 /
Conventional Commits 分组 / 重构 ReportDialog / 一键推送飞书钉钉企微 /
多通道多模板管理 / 导出 .md / i18n bundle / 菜单快捷键展示。

## [1.3.0] - 2024

- 支持任意 OpenAI 兼容 LLM 厂商（智谱 / DeepSeek / 通义千问 / Kimi / Ollama 等）
- 「今日提交记录」「生成今日日报」聚合 IDEA 项目下所有 Git 仓库，按 仓库 → 分支 两级分组
