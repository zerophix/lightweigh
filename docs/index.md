# AI Project Collaboration Blueprint

> AI 与人类协作的通用协议模版 — 定义规则、流程和文档规范。

---

## 项目概述

本项目提供了一套完整的 AI 协作框架，包括文档架构、生命周期流程、校验体系、Git 工作流和上下文管理。目标是让任何 AI 接入新项目时，能够快速理解规则并高效协作。

**v1.0.1（当前）**：修复 06_Metrics.md 被 lifecycle.sh 覆盖为模板的问题，完善黄金标准晋升。**v1.0.0**：模版初始版本，包含完整的文档结构、校验体系和协作协议。

---

## 文档导航

### AI 过程文档

<!-- 这些是 AI 工作过程中维护的文档。 -->

| 文档 | 用途 | AI 职责 |
|------|------|---------|
| [PRD](ai/lifecycle/01_PRD.md) | 产品需求定义 | 每次迭代定义目标和故事 |
| [架构设计](ai/lifecycle/02_Architecture.md) | 系统架构 | 架构变更时更新 |
| [任务图](ai/lifecycle/03_TaskGraph.md) | 任务分解 | 持续更新状态 |
| [开发笔记](ai/lifecycle/04_DevNotes.md) | 技术记录 | 开发中追加 |
| [决策日志](ai/decisions/index.md) | 工程决策 | 追加写，不覆盖 |
| [设计笔记](notes/index.md) | 设计讨论 | 未定型内容 |
| [约束规则](ai/constraints/index.md) | 行为边界 | AI 首次执行前必读 |

### 黄金标准文档

<!-- 这些是晋升后的稳定文档。不可直接编辑，必须经 promote.sh 晋升。 -->

| 文档 | 用途 | 晋升来源 |
|------|------|---------|
| [架构概览](architecture/index.md) | 当前系统架构 | lifecycle/02_Architecture.md |
| [开发指南](guides/index.md) | 开发指引 | lifecycle/01_PRD.md |
| [技术参考](reference/index.md) | 接口/协议 | 晋升 |
| [变更日志](changelog/index.md) | 版本变更 | DEC 晋升 |
