# 多 AI 并发协作协议

> 适用：多名 AI（或 AI + 人类）同时推进本仓库。
> 权威任务板：`.ai/tasks.json`。合入前门禁：`bash scripts/governance/gov-local-ci.sh`。
> 决策：DEC-047、DEC-048、DEC-049、DEC-050、DEC-054。
>
> **真源声明**：本文件是 Notion「轻衡健康 / 04-研发 / DOC-并发开发约定」的工程侧镜像。并发约定以 Notion 为准，冲突时以 Notion 为准并回同步本文件。

---

## 1. 核心原则

1. **一写者原则**：同一 git 工作树同一时刻只允许一个会话写入源码/文档。
2. **一 AI 一分支 / 一批次一分支**：
   - 单任务分支：`feat|fix|docs|refactor|chore|test|ci/<topic>`
   - **批次分支**：`batch/<B-xxx>`（如 `batch/B-w1-screens`），同批次任务共享
   - 禁止在 `master` 上直提交（pre-commit 已拦）。
3. **优先 worktree**：
   - 单任务：`bash scripts/session/ses-worktree.sh add feat/<topic>`
   - **批次**：`bash scripts/session/ses-worktree.sh batch-add B-xxx [--in-repo]`（默认 `.worktrees/B-xxx/`）
   - hooks 已优先 `show-toplevel`（DEC-050）。
4. **不共享未提交工作**：不要在别人的脏树上继续改；认领文件后再改。
5. **合入看门禁**：本地 `gov-local-ci.sh` 全绿（含 gitleaks 密钥扫描），再开 PR；特性分支 **push 也会跑 CI**（DEC-049）；远程无分支保护时，**PR 模板勾选是纪律硬门槛**。
6. **无 AC 不开工**：任务须具备 ready 的 `acceptance`（见 [GC-task-acceptance](GC-task-acceptance.md)）。

## 2. 会话生命周期

```bash
bash scripts/session/ses-session-begin.sh
git add <files>
bash scripts/session/ses-claim-staged.sh
git commit -m "type(scope): summary"
bash scripts/session/ses-session-end.sh
```

若缺少 `current-session.json`，pre-commit **跳过**归属检查——并发场景下**必须** begin。

## 3. 何时写 DEC

| 变更类型 | 需要 DEC |
|----------|----------|
| 协议 / 算法 / Room schema / 同步语义 | 是 |
| 治理门禁、约束退役、协作协议 | 是 |
| 纯文案、注释、死 import 清理 | 通常否（DevNotes 一行即可） |
| 架构事实 | 改 `docs/ai/lifecycle/02_Architecture.md` 后 `wf-promote.sh` |

编号：`bash scripts/governance/gov-next-decision.sh --create`（带锁）。

## 4. codebase-memory 与文档

- **codebase-memory（用户级 MCP）**：查「在哪 / 怎么连」。
- **DEC / 架构文档**：查「为什么」；任务状态以 `.ai/tasks.json` 为准。
- 发现文档与代码冲突时，先信代码与测试，再立 DEC 修文档（见 DEC-045）。

## 5. 冲突与恢复

| 症状 | 处理 |
|------|------|
| pre-commit 报未声明归属 | `ses-claim-staged.sh` 或确认是否误碰他会话文件 |
| custom-checks 红（死 import 等） | 先修再提交；勿习惯性 `--no-verify` |
| 两会话改同一文件 | 后写者停手；用 worktree/分支重放；必要时人类裁定 |
| `bs-install-hooks` 后 hook 行为异常 | 确认安装的是 simplified（DEC-047）；`diff` 对照 `gov-pre-commit-simplified` |

## 6. 推荐日常顺序（单任务 / 批次两种模式）

### 单任务模式
1. begin → 读 STATUS / tasks.json / inbox  
2. 向人类确认 next_action；确认任务 AC ready  
3. `bash scripts/session/ses-worktree.sh add feat/<topic>` → 改代码 → claim → commit  
4. `bash scripts/governance/gov-local-ci.sh`  
5. push → 开 PR（**使用仓库 PR 模板勾选清单**）→ 人类或授权合并  
6. session-end

### 批次模式（DEC-082：同批次任务共享 worktree）
1. begin → 读 STATUS / tasks.json / inbox  
2. 向人类确认 next_action；确认任务 AC ready（`wf-parallel.sh plan` 查看批次分支）  
3. `bash scripts/workflow/wf-autopilot.sh start T-072`（自动创建/复用批次 worktree）  
   - 同批次后续任务：`bash scripts/workflow/wf-autopilot.sh start T-073`（复用 worktree）  
4. 改代码 → claim → commit（每任务独立锁，`wf-claim.sh claim T-xxx --batch B-xxx`）  
5. `bash scripts/governance/gov-local-ci.sh`（车道按变更路径自动判定）  
6. 批次完成：`bash scripts/workflow/wf-autopilot.sh batch-finish B-w1-screens`（批量校验 → 批次 PR）  
7. session-end  

## 7. PR 合入清单（DEC-048）

开 PR 时必须使用 `.github/PULL_REQUEST_TEMPLATE.md`，至少勾选并填写：

- 已跑 `gov-local-ci.sh` 且粘贴 PASS 摘要  
- 任务 AC / DoD 已覆盖  
- 未静默改黄金向量 / Room schema（有改必写 DEC）  
- DEC / 关联任务 ID  

无 GitHub Pro、无 required checks 时，**未勾选则视为不可合入**。


## 8. Worktree 命令（DEC-050 / DEC-082）

### 8.1 单任务 worktree（传统模式）
```bash
bash scripts/session/ses-worktree.sh add feat/my-topic
bash scripts/session/ses-worktree.sh add fix/ble-scan --in-repo
bash scripts/session/ses-worktree.sh list
bash scripts/session/ses-worktree.sh doctor          # 确认 hooks ROOT=本树
bash scripts/session/ses-worktree.sh remove ../lightweigh-my-topic
```

### 8.2 批次 worktree（DEC-082：同批次任务共享分支）
```bash
# 创建/复用批次 worktree
bash scripts/session/ses-worktree.sh batch-add B-w1-screens [--in-repo]
bash scripts/session/ses-worktree.sh batch-list

# 认领批次内任务（每任务独立锁，防重复实现）
bash scripts/workflow/wf-claim.sh claim T-072 --batch B-w1-screens

# 自动驾驶启动（自动识别批次，复用 worktree）
bash scripts/workflow/wf-autopilot.sh start T-072
bash scripts/workflow/wf-autopilot.sh start T-073  # 复用同一批次 worktree

# 批次完成（校验所有任务 → 批次 PR）
bash scripts/workflow/wf-autopilot.sh batch-finish B-w1-screens
```

### 8.3 清理
```bash
bash scripts/session/ses-worktree.sh clean [--dry-run]  # 仅清理真正已合入的非批次 worktree；批次分支需手动清理
```

创建后按脚本打印的 begin → claim → local-ci → push 顺序工作。

## 来源

DEC-047、DEC-048、DEC-049、DEC-050、DEC-054；前序 DEC-045（文档事实）、DEC-046（custom-checks / local-ci）。
