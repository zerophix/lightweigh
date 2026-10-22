# 多 AI 并发协作协议

> 适用：多名 AI（或 AI + 人类）同时推进本仓库。
> 权威任务板：`.ai/tasks.json`。合入前门禁：`bash scripts/governance/gov-local-ci.sh`。
> 决策：DEC-047、DEC-048、DEC-049、DEC-050、DEC-054。

---

## 1. 核心原则

1. **一写者原则**：同一 git 工作树同一时刻只允许一个会话写入源码/文档。
2. **一 AI 一分支**：分支名 `feat|fix|docs|refactor|chore|test|ci/<topic>`；禁止在 `master` 上直提交（pre-commit 已拦）。
3. **优先 worktree**：`bash scripts/session/ses-worktree.sh add feat/<topic>`（默认 `../lightweigh-<topic>`；`--in-repo` 用 `.worktrees/`）。hooks 已优先 `show-toplevel`（DEC-050）。
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

## 6. 推荐日常顺序

1. begin → 读 STATUS / tasks.json / inbox  
2. 向人类确认 next_action；确认任务 AC ready  
3. 建分支或 worktree → 改代码 → claim → commit  
4. `bash scripts/governance/gov-local-ci.sh`  
5. push → 开 PR（**使用仓库 PR 模板勾选清单**）→ 人类或授权合并  
6. session-end  

## 7. PR 合入清单（DEC-048）

开 PR 时必须使用 `.github/PULL_REQUEST_TEMPLATE.md`，至少勾选并填写：

- 已跑 `gov-local-ci.sh` 且粘贴 PASS 摘要  
- 任务 AC / DoD 已覆盖  
- 未静默改黄金向量 / Room schema（有改必写 DEC）  
- DEC / 关联任务 ID  

无 GitHub Pro、无 required checks 时，**未勾选则视为不可合入**。


## 8. Worktree 命令（DEC-050）

```bash
bash scripts/session/ses-worktree.sh add feat/my-topic
bash scripts/session/ses-worktree.sh add fix/ble-scan --in-repo
bash scripts/session/ses-worktree.sh list
bash scripts/session/ses-worktree.sh doctor          # 确认 hooks ROOT=本树
bash scripts/session/ses-worktree.sh remove ../lightweigh-my-topic
```

创建后按脚本打印的 begin → claim → local-ci → push 顺序工作。

## 来源

DEC-047、DEC-048、DEC-049、DEC-050、DEC-054；前序 DEC-045（文档事实）、DEC-046（custom-checks / local-ci）。
