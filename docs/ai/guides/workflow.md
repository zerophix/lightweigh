# AI 工作流程

> AI 与人类协作的执行流程。每个 session 按此顺序执行。
>
> **真源声明**：本文件是 Notion「轻衡健康 / 00-规范 / DOC-开发流程总图」的工程侧镜像。
> 工作流程以 Notion 为准，冲突时以 Notion 为准并回同步本文件。

---

## 三步开工

```bash
bash scripts/session/ses-session-begin.sh        # 会话 ID + 上下文 + 合规卡
bash scripts/workflow/wf-role.sh claim <role>    # dev | gov | docs | review
bash scripts/workflow/wf-autopilot.sh status     # 一屏真相 + NEXT 命令
```

- 一个会话只持有一个角色，8 小时过期。
- 未声明角色不得改代码或治理脚本。
- 多 AI 并发时必须使用 worktree。

## 四环模型（快慢分离）

| 环 | 预算 | 命令 | 跑什么 |
|----|------|------|--------|
| 内环 | ≤ 5s | `scripts/dev/dev-loop.sh [--watch]` | 增量编译 + 同名测试 + 受影响 pytest |
| 交付 | ≤ 5s | `scripts/dev/ship.sh -m "..." <path>` | 暂存 + 提交 + 推送 + PR |
| 提交环 | ≤ 3s | `git commit` | 分支/范围/角色/`bash -n`/暂存区检查 |
| 批次环 | ≤ 10min | `bash scripts/governance/gov-local-ci.sh` | 全量测试 + 静态检查 |

## 任务执行链路

```
认领 ──► worktree ──► 写码 ──► 提交 ──► PR ──► 队列验证 ──► 合入
```

- `wf-claim.sh claim T-xxx` — 原子认领
- `ses-worktree.sh add feat/<topic>` — 单任务隔离工作区
- `ses-worktree.sh batch-add B-xxx` — 批次 worktree（DEC-082，同批次任务共享）
- `wf-claim.sh claim T-xxx --batch B-xxx` — 认领批次内任务
- `wf-autopilot.sh start T-xxx` — 自动识别批次，复用 worktree
- `wf-autopilot.sh batch-finish B-xxx` — 批次完成：批量 DoD → 批次 PR
- `gov-task-done.sh T-xxx` — DoD 验证，生成证据
- `wf-queue.sh run` — 批次验证 + 合并

## 核心原则

1. **不进则退** — 不在未通过 `gov-validate.sh` 的状态下提交
2. **追加不覆盖** — 决策日志（DEC-*）只追加写，不删除不修改
3. **晋升不直接写** — 黄金标准通过 `wf-promote.sh`，不可直接编辑 `docs/` 根目录文件
4. **重大变更须有 DEC** — 协议/算法/schema/同步语义/治理门禁/架构事实须写 DEC；纯文案、注释、死 import 清理通常不需要

## 共享文件归则

- `.ai/tasks.json`、`STATUS.md`、`docs/ai/lifecycle/03_TaskGraph.md`、`docs/ai/lifecycle/06_Metrics.md`、`docs/index.md`、`AGENTS.md` — 只能由合并队列/收尾自动化写入
- 功能分支只提交：**自己的代码 + 自己的测试 + 自己的文档**

## 命令速查

- **流程**：`wf-autopilot.sh` → `wf-claim.sh` → `wf-task-board.sh` → `wf-queue.sh`
- **开发**：`dev-loop.sh` → `dev-verify.sh` → `ship.sh`
- **门禁**：`gov-validate.sh` → `gov-local-ci.sh` → `gov-task-done.sh` → `gov-merge.sh`

## 参考

- [决策流转规范](../../guides/GC-decision-flow.md) — NOTE→DEC→promote→golden 完整链路
- [校验规则](../../guides/GC-decision-flow.md) — 见 GC-decision-flow.md 校验体系章节
- [多 AI 并发协作](../../guides/GC-multi-agent-collab.md) — worktree / 合并队列 / 共享文件
- [原型驱动开发](../../guides/GC-prototype-driven-dev.md) — Wave 0/1/2 执行顺序
