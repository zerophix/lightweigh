---
name: dev-flow
description: >
  开发流程自动驾驶入口。任何 AI 进入本仓库、或要判断"现在该做什么/怎么推进"时使用。
  一切以脚本输出为准：wf-autopilot.sh status 给出任务批次、在途 PR、worktree 卫生与下一步命令。
  触发词：开工、推进、合并、清理、状态、dev-flow、autopilot。
---

# 开发流程自动驾驶（dev-flow）

**本 skill 不靠文字约束，靠命令执行。** 你只需要按顺序运行下面的命令，并照输出做。

## 0. 进入仓库后的第一条命令

```bash
bash scripts/workflow/wf-autopilot.sh status
```

它会告诉你四件事，**以它为准，不要自己猜**：

1. 任务板下一批可并行任务（来自 `wf-parallel.sh plan`）
2. 所有在途 PR（编号 / 分支 / 是否可合 / 对应 worktree）
3. worktree 审计：哪些已合入应删除、哪些在途、哪些是孤儿目录
4. `【下一步】` 给出的那条命令

## 1. 按 NEXT 执行

| NEXT 显示 | 你执行 | 说明 |
|---|---|---|
| `advance` | `bash scripts/workflow/wf-autopilot.sh advance` | 自动逐个：同步 master → 派生物同步 → local-ci → 合入 → 删 worktree/分支。遇语义冲突会停下并列出文件 |
| `start <T-XXX>` | `bash scripts/workflow/wf-autopilot.sh start T-XXX` | 认领锁 + 建 worktree + 打印目标文件与验收 |
| 无任务无 PR | 停下来问人类 | 不要自造任务 |

## 2. 写代码期间

- 慢命令（gradle / local-ci）一律 `wf-run.sh start … `，不要前台阻塞。
- 独立任务并行：`wf-parallel.sh plan` 给出批次，每任务一个 worktree/子代理。
- **只写任务 `target_files` + 同模块测试**；不碰 `.ai/tasks.json`、`docs/`、`STATUS.md`、注册表、AGENTS.md。

## 3. 收尾

```bash
bash scripts/governance/gov-task-done.sh T-XXX     # 机械 DoD：文件/原型/verify_commands
bash scripts/workflow/wf-autopilot.sh finish T-XXX  # 提交 + 推送 + 开 PR
bash scripts/workflow/wf-autopilot.sh advance       # 合入并清理
```

## 4. 硬边界（全部有脚本执行者）

| 规则 | 执行者 |
|---|---|
| 原型任务必须先认领 | `wf-claim.sh` + pre-commit |
| 不合入就不算完成 | `gov-task-done.sh` |
| 合入只有一条路径 | `wf-autopilot.sh advance` / `gov-merge.sh` |
| worktree 不残留 | advance 自动删 + `gov-workspace-doctor.sh --fix` |
| 慢命令不阻塞 | `wf-run.sh`（约定，但执行器让正确做法最省事） |

更完整的规则映射见 `AGENTS.md` §10。
