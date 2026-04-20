<div align="center">

# 📰 daily_newspaper_generator

**一键将 Git 提交记录转换为结构化日报 / 周报 · IntelliJ IDEA 插件**

*Turn your Git commits into structured daily / weekly reports — right inside IntelliJ IDEA*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.x%2B-000000?logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Build](https://img.shields.io/badge/Build-Gradle-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[📖 中文说明](#-项目简介) · [🚀 快速开始](#-快速开始) · [✨ 功能亮点](#-功能亮点) · [💬 反馈](https://github.com/movebrickschi/daily_newspaper_generator/issues)

</div>

---

## 🆕 项目简介

还在为了写日报、周报逆向回忆今天做了什么吗？

`daily_newspaper_generator` 是一款 **IntelliJ IDEA 插件**，能够在 IDE 中一键选择时间范围、作者、项目范围，自动汇总你的 **Git 提交记录**，生成可以直接复制发送的 **日报、周报、月报**。

> 一句话总结：打开插件 → 选择日期 → 生成 → 复制发送给领导。告别手写工作汇报。

---

## ✨ 功能亮点

- 📅 **多时间维度**：支持今日 / 昨日 / 本周 / 上周 / 本月 / 自定义区间等多种时间维度。
- 🔍 **多维度过滤**：按作者 / 项目 / 分支 / 关键字过滤，只要你想要的那部分。
- 📝 **结构化输出**：自动按项目 / 模块 / 日期分组，输出 Markdown / 纯文本 两种格式。
- 🎯 **精准去重**：自动过滤合并 commit、Revert、重复提交信息。
- 📎 **一键复制**：生成后一键复制到剪贴板，不需要手工整理。
- 🔒 **本地运行**：所有数据处理均在本地进行，不上传任何仓库信息，安全放心。

---

## 🚀 快速开始

### 安装插件

#### 方式一：从源码构建

```bash
git clone https://github.com/movebrickschi/daily_newspaper_generator.git
cd daily_newspaper_generator
./gradlew buildPlugin
```

构建产物位于 `build/distributions/` 目录。

#### 方式二：本地调试

```bash
./gradlew runIde
```

会启动一个预装本插件的 IDEA 沙箱实例，方便开发调试。

#### 方式三：安装到本地 IDE

1. IntelliJ IDEA → **Settings** → **Plugins** → **⚙️** → **Install Plugin from Disk…**
2. 选择 `build/distributions/daily_newspaper_generator-*.zip`
3. 重启 IDE

---

## 📘 使用方式

1. 打开 Git 项目。
2. 从菜单栏调出插件面板。
3. 选择时间范围、作者、项目范围。
4. 点击 **生成**，即可得到当前筛选条件下的提交汇总。
5. 一键复制，发给领导或贴到 OA 、釘钉、企业微信。

### 输出示例

```
📅 工作日报（2026-04-20）

📦 项目：order-service
  • feat(order): 实现订单合并付款逻辑
  • fix(order): 修复高并发下订单号重复问题
  • perf(order): 优化列表查询索引，响应从 800ms 降至 80ms

📦 项目：payment-service
  • feat(refund): 接入退款回调接口
  • chore(deps): 升级 spring-boot 到 3.2.5
```

---

## 🏗️ 技术栈

- **语言**：Kotlin
- **构建**：Gradle Kotlin DSL
- **平台**：IntelliJ Platform SDK
- **Git 集成**：IntelliJ Platform Git4Idea API

---

## 💡 适用场景

| 场景 | 代价 | 使用本插件后 |
| --- | --- | --- |
| 每日工作日报 | 10–20 分钟回忆 + 整理 | ≤ 30 秒生成 |
| 周报 / 月报 | 翻一周、一个月记忆 | 一键生成 |
| 项目阶段性趍趍报告 | 手动扫 commit log | 质量与覆盖度可控 |
| 多人项目贡献总结 | 手动联联联 | 按作者一键过滤 |

---

## 🤝 参与贡献

欢迎提 Issue / PR。计划中的能力：

- [ ] 支持多仓库同时汇总
- [ ] AI 辅助总结（提炼成果性语言）
- [ ] 一键发送到 釘钉 / 企微 机器人
- [ ] 可自定义报告模板

---

## 📜 License

[MIT](LICENSE) © [movebrickschi](https://github.com/movebrickschi)

---

<div align="center">

**如果这个插件帮到了你，请点个 Star ⭐**<br/>
*If this plugin saves your day, please leave a star ⭐*

[⚡ 查看作者其他项目 / See more works](https://github.com/movebrickschi)

</div>
