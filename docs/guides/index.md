# 开发指南

> 本项目的开发规范、操作流程和最佳实践集合。

---

## 概述

此目录收录轻衡健康 Android（lightweigh）的规范文档：既有治理框架自身的接入 / 迁移 /
决策流转规范，也有 README 结构契约与 AI 编码约定。治理框架源自 ai-project-template。

---

## 指南索引

| 指南 | 适用场景 | 内容概述 |
|------|---------|---------|
| [新项目接入](GC-new-project-setup.md) | 全新项目 | 一键安装模板，自动复制平台配置、安装 git 钩子、构建文档站点 |
| [已有项目迁移](GC-existing-project-porting.md) | 存量项目 | 将协作模式逐步移植到已有项目中，分阶段采纳而不破坏现有结构 |
| [决策流转规范](GC-decision-flow.md) | 所有 AI 助手 | 从设计笔记→决策日志→生命周期→晋升→黄金标准的全链路规范 |
| [README 结构规范](GC-readme-structure.md) | 所有 AI 助手 | README 目录层次标准：机读层结构契约（GOV-README 校验）+ 手读层标准大纲与反例 |
| [多 AI 并发协作](GC-multi-agent-collab.md) | 多会话 / 多代理 | 一写者原则、worktree/分支、会话 claim、gov-local-ci、PR 清单（DEC-047/048） |
| [任务验收标准](GC-task-acceptance.md) | 所有任务执行 | `.ai/tasks.json` 的 acceptance schema；无 ready AC 不开工（DEC-048） |
| [编码约定](GC-coding-conventions.md) | 写代码的 AI/人 | 模块地图、Gradle 矩阵、四件套、Room/黄金禁区（DEC-048） |
| [工程反思（自愿）](GC-engineering-reflections.md) | 有真实教训时 | 自愿追加；强制反思门禁已退役 |

---

## 使用建议

- **新用户**：先读 GC-new-project-setup.md 完成项目初始化
- **迁移用户**：读 GC-existing-project-porting.md 了解分阶段迁移策略
- **所有 AI**：每次启动必须读 GC-decision-flow.md；编码前读 GC-coding-conventions 与任务 AC
- **多会话并发**：先读 GC-multi-agent-collab.md，再 begin / claim；合入用 PR 模板

## 来源: ai/decisions/DEC-048
