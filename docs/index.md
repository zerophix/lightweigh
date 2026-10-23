# 轻衡健康 Android（lightweigh）

> 蓝牙体脂秤测量的记录、展示与同步 — DDD 分层 Android 工程的文档站。

> 更新时间：2026-10-23

---

## 真源声明

本仓库 `docs/` 与 Notion「轻衡健康」空间是**分工关系，不是备份关系**。

| 关切 | 权威方 |
|------|--------|
| 产品范围、验收标准（AC）、产品决策 | **Notion**（`01-产品` 下的 `PRD-*` / `DEC-*`） |
| 工程决策、架构、编码规范、原型、任务、治理脚本 | **本仓库** |

冲突时：产品口径以 Notion 为准并回同步；工程口径以本仓库 DEC 为准并回同步。
归属规则全文见 [GC-decision-flow](guides/GC-decision-flow.md) 第 0 层；
Notion 侧对应规范为「00-规范 / DOC-目录与命名规范」与「DOC-需求到编码链路」。

---

## 项目概述

lightweigh 是轻衡健康 Android 客户端，采用 DDD 分层四模块工程，包坐标 **com.xcwk.scalehub**。
它由旧库 qk-health-android 混合式重构而来。

当前主路径已落地：手动与蓝牙测量落库、最近列表与详情、历史筛选、多成员档案与目标进度、
云同步（水位线 / 对账 / 幂等）与登录（手机号 / 邮箱 / 微信入口）、蓝牙三协议与真机秤连通、
习惯管理与提醒、趋势与报告与设置等页面主体。

**未完成项以仓库根目录 `.ai/tasks.json` 的 pending 任务为准**，不以本页或历史里程碑判断。

---

## 文档导航

### 产品与需求（权威在 Notion）

| 文档 | 用途 |
|------|------|
| [PRD](ai/lifecycle/01_PRD.md) | 产品需求与里程碑（Notion `PRD-产品需求` 的工程侧镜像） |
| [架构设计](ai/lifecycle/02_Architecture.md) | 四模块分层架构 |
| [任务图](ai/lifecycle/03_TaskGraph.md) | 任务分解与状态 |
| [开发笔记](ai/lifecycle/04_DevNotes.md) | 技术记录与问题 |
| [测试](ai/lifecycle/05_Test.md) | 验证策略 |
| [指标](ai/lifecycle/06_Metrics.md) | 项目健康指标 |

### 工程过程文档

| 文档 | 用途 |
|------|------|
| [决策日志](ai/decisions/index.md) | 工程决策（追加写，`gov-next-decision.sh` 编号） |
| [设计笔记](notes/index.md) | 设计讨论 |
| [约束规则](ai/constraints/index.md) | 行为边界 |
| [工作指南](ai/guides/index.md) | AI 协作流程与文档标准 |
| [评审报告](ai/reviews/index.md) | 多 AI 评审产出 |
| [信箱](ai/inbox/) | 会话间接力消息 |

### 原型与设计输入

| 位置 | 用途 |
|------|------|
| `design/prototypes/interactive/` | 26 个交互原型 HTML，192 屏，**UI/UX 唯一真相源** |
| `design/prototypes/theme.css` | 设计系统 token 单一来源 |
| `design/page-list-TODO.md` | 页面清单概览与功能性缺口 |

### 黄金标准文档

| 文档 | 用途 | 晋升来源 |
|------|------|---------|
| [架构概览](architecture/index.md) | 当前系统架构 | lifecycle/02_Architecture.md |
| [开发指南](guides/index.md) | 开发指引与规范 | lifecycle/01_PRD.md |
| [技术参考](reference/index.md) | 接口 / 协议 | 晋升 |
| [变更日志](changelog/index.md) | 版本变更 | DEC 晋升 |

### 治理与验证

| 命令 | 用途 |
|------|------|
| `bash scripts/governance/gov-validate.sh` | 全量治理校验，违规数须为 0 |
| `python3 scripts/governance/gov-prototype-verify.py --all --strict` | 原型→代码闭环验证 |
| `bash scripts/governance/gov-local-ci.sh` | 合入前本地 CI |
| `bash scripts/workflow/wf-task-board.sh list` | 任务板状态 |
