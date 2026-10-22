# AI 工作流程

> AI 与人类协作的 10 步生命周期。每个 session 按此顺序执行。

---

## 生命周期 10 步

```
D→R→E→T→A→V→M→L→D→P
```

| 步骤 | 名称 | AI 行为 |
|------|------|---------|
| **D** | 读取状态 | 读 STATUS.md → context-load.sh → 最近 notes + decisions |
| **R** | 多 AI 评审 | 按维度审查，写报告到 reviews/{date}_{topic}/{agent}.md |
| **E** | 决策引擎 | 生成方案，评分选择 |
| **T** | 任务图 | 分解为可执行任务 |
| **A** | 执行 | 写代码、改文档、创建 DEC |
| **V** | 验证 | validate.sh → 测试 → 修复 |
| **M** | 更新指标 | update-status.sh → update-metrics.sh |
| **L** | 记录决策 | next-decision.sh --create → 编辑 DEC |
| **D** | 更新文档 | 覆盖写入 lifecycle/ 6 个核心文档 |
| **P** | 晋升 | promote.sh → sync-standards.sh |

## 核心原则

1. **不进则退** — 不在未通过 validate.sh 的状态下提交
2. **追加不覆盖** — 决策日志只追加写，不删除不修改
3. **晋升不直接写** — 黄金标准必须通过 promote.sh，不直接编辑 docs/ 根目录文件
4. **重大变更须有 DEC** — 协议/算法/schema/同步语义/治理门禁/架构事实须写 DEC；纯文案、注释、死 import 清理通常不需要（见 AGENTS.md / GC-multi-agent-collab / DEC-055）

## 文件管理

- AI 只能写入 AGENTS.md 白名单内的文件
- 禁止创建 old/legacy/vN 版本目录
- 新指南写 GC-{slug}.md，新参考写 REF-{slug}.md
- 散落旧文件用 migrate.sh --archive 归档到 docs/archive/

## 参考

- [决策流转规范](../../guides/GC-decision-flow.md) — NOTE→DEC→promote→golden 完整链路
- [校验规则](../../guides/GC-decision-flow.md) — 见 GC-decision-flow.md 校验体系章节
