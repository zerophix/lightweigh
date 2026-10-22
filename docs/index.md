# 轻衡健康 Android（lightweigh）

> 蓝牙体脂秤测量的记录、展示与同步 — DDD 分层 Android 工程的文档站。

---

## 项目概述

lightweigh 是轻衡健康 Android 客户端，采用 DDD 分层四模块工程，包坐标 **com.xcwk.scalehub**。
它由旧库 qk-health-android 混合式重构而来，当前处于 **M1（可运行骨架）完成、M2（云同步与登录）
待启动**的阶段。本页是文档站入口，向下收录 AI 过程文档与黄金标准文档。

---

## 文档导航

### AI 过程文档

| 文档 | 用途 |
|------|------|
| [PRD](ai/lifecycle/01_PRD.md) | 产品需求与里程碑 |
| [架构设计](ai/lifecycle/02_Architecture.md) | 四模块分层架构 |
| [任务图](ai/lifecycle/03_TaskGraph.md) | 任务分解与状态 |
| [开发笔记](ai/lifecycle/04_DevNotes.md) | 技术记录与问题 |
| [测试](ai/lifecycle/05_Test.md) | 验证策略 |
| [指标](ai/lifecycle/06_Metrics.md) | 项目健康指标 |
| [决策日志](ai/decisions/index.md) | 工程决策（追加写） |
| [设计笔记](notes/index.md) | 设计讨论 |
| [约束规则](ai/constraints/index.md) | 行为边界 |

### 黄金标准文档

| 文档 | 用途 | 晋升来源 |
|------|------|---------|
| [架构概览](architecture/index.md) | 当前系统架构 | lifecycle/02_Architecture.md |
| [开发指南](guides/index.md) | 开发指引 | lifecycle/01_PRD.md |
| [技术参考](reference/index.md) | 接口/协议 | 晋升 |
| [变更日志](changelog/index.md) | 版本变更 | DEC 晋升 |
