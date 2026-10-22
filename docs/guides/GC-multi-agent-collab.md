# 多 AI 并发协作协议

> 适用：多名 AI（或 AI + 人类）同时推进本仓库。
> 权威任务板：`.ai/tasks.json`。合入前门禁：`bash scripts/governance/gov-local-ci.sh`。
> 决策：DEC-047。

---

## 1. 核心原则

1. **一写者原则**：同一 git 工作树同一时刻只允许一个会话写入源码/文档。
2. **一 AI 一分支**：分支名 `feat|fix|docs|refactor|chore|test|ci/<topic>`；禁止在 `master` 上直提交（pre-commit 已拦）。
3. **优先 worktree**：`git worktree add ../qk-health-wt-<topic> -b feat/<topic>`；未验证 worktree 钩子前，也可约定「主树独占 + 其他会话只读」。
4. **不共享未提交工作**：不要在别人的脏树上继续改；认领文件后再改。
5. **合入看门禁**：本地 `gov-local-ci.sh` 全绿，再开 PR；远程 CI / 分支保护由人类配置。

## 2. 会话生命周期

```bash
# 开始（写入 .ai/current-session.json，已 gitignore）
bash scripts/session/ses-session-begin.sh

# 暂存后、提交前：声明文件归属（写入 .ai/staged-claims.json，已 gitignore）
git add <files>
bash scripts/session/ses-claim-staged.sh

# 提交（pre-commit：归属检查 + 源码时 compile + gov-custom-checks）
git commit -m "type(scope): summary"

# 结束（删除 current-session，并清理本会话 claims）
bash scripts/session/ses-session-end.sh
```

若缺少 `current-session.json`，pre-commit **跳过**归属检查——因此并发场景下**必须** begin。

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
2. 向人类确认 next_action  
3. 建分支或 worktree → 改代码 → claim → commit  
4. `bash scripts/governance/gov-local-ci.sh`  
5. push → 开 PR → 人类合并  
6. session-end  

## 来源

DEC-047；前序 DEC-045（文档事实）、DEC-046（custom-checks / local-ci）。
