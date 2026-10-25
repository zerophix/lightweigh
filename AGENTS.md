# Project Collaboration Blueprint — AI 协作协议

> 权威任务板：`.ai/tasks.json`。本文件是唯一操作手册：任何 AI 读完即可开工。
> 每条规则都标注**执行者**（机械强制 / 约定）；没有执行者的规则不假装是门禁。

---

## 0. 治理总开关（写代码前先看这条）

```bash
bash scripts/governance/gov-mode.sh off 2h   # 纯写码：跳过认领锁/文档校验/密钥扫描/队列/DoD
bash scripts/governance/gov-mode.sh on
bash scripts/governance/gov-mode.sh status
```

- 作用域：默认**每个 worktree 各一份**（`.ai/gov-mode.json`），互不影响；同 worktree 多会话用 `GOV_SESSION=xxx gov-mode.sh off` 隔离；单条命令可用 `GOV_MODE=off git commit ...`。
- 有期限：默认 2h，**上限 8h**，到期自动回到 on；off 期间每次 commit/push 会打印一行提示（`GOV_QUIET=1` 静音），跳过记录写在 `.ai/gov-skips/`。
- 钩子来源：`core.hooksPath` 指向主仓库的**绝对路径**，所有 worktree 共用同一份，旧分支绕不过开关；用 `gov-hook-doctor.sh --fix` 校正。
- 不受开关影响：`gradlew build`、`pytest`、装机验证——它们才是"完成"的依据。

## 0. 30 秒启动

```bash
cat STATUS.md                                # 当前状态与下一步
bash scripts/session/ses-context-load.sh     # 上次会话上下文
bash scripts/workflow/wf-inbox.sh list       # 未读接力信（读即可，无时效门禁）
bash scripts/workflow/wf-claim.sh status     # 哪些任务已被占用
bash scripts/governance/gov-workspace-doctor.sh   # 工作区是否干净（秒级）
```

## 0.5 并发开发三件套（多 AI 同时写代码时）

```bash
scripts/dev/who-is-working.sh          # 谁在哪个 worktree、改到哪些模块、开关状态
scripts/dev/dev-verify.sh              # 编译 → 装机 → 启动（+ --journey 跑一条路径）
scripts/dev/merge-mine.sh              # rebase origin/master → 推送（撞车自动重试）
```

- `TOUCHED` 有重叠先谈好归属；`⚠共享文件`（AGENTS.md / .ai/tasks.json / docs/ / .github/ / 测试注册表）只由一人改。
- `merge-mine.sh` 只做快进推送，不解冲突；真冲突它会列出文件并退出。
- 想做"单写者"模式时：其他人 `merge-mine.sh --branch-only` 只推分支，由一人合 master。

## 1. 开工决策表

| 你要做的事 | 动作 |
|---|---|
| 写功能 / 修 bug | ① `wf-claim.sh claim T-xxx` → ② `ses-worktree.sh add <type>/<topic>` → ③ 写代码+测试 → ④ commit/push → ⑤ 开 PR → ⑥ `gov-merge.sh <PR>` |
| 只改文档 | 同上（车道 A，秒级门禁） |
| 合入别人的 PR | `bash scripts/governance/gov-merge.sh <PR号>`（冲突时它会停下并给指引） |
| 收尾 | `bash scripts/session/ses-session-end.sh`（自动跑 workspace doctor） |
| 判断哪些任务能并行 | `bash scripts/workflow/wf-parallel.sh plan` |

认领失败（锁被他人持有）→ **换任务，不要重复实现**。

## 2. 执行模式（机械规则，不靠临场判断）

| 场景 | 模式 | 做法 |
|---|---|---|
| gradle 构建、`:app:testDebugUnitTest`、`gov-local-ci.sh`、全量 pytest | **后台** | `wf-run.sh start <name> -- <命令>`，然后继续做别的事；需要结果时 `wf-run.sh wait <id>` |
| pre-commit / pre-push / `gov-validate` / `gov-workspace-doctor` | 前台 | 秒级，直接跑 |
| 多个**文件不重叠**的独立任务 | **并行** | `wf-parallel.sh plan` 得批次 → 每任务一个子代理 + 一个 worktree |
| 同一工作区的两个校验器 | **串行** | 会互相写文件（实测假违规） |

铁律：**>10 秒的命令不得前台阻塞**；后台必须用 `wf-run.sh`（裸 `&` 拿不到退出码，禁止）。

## 3. worktree 生命周期（状态机）

```
认领任务 ──► ses-worktree.sh add <type>/<topic>（自动带 local.properties）
              │
              ▼
          cd 到该树 → ses-session-begin.sh → 编码/提交/推送
              │
              ▼
          PR 合入时 gov-merge.sh 自动删除 worktree + 本地/远端分支
              │
              ▼
          会话结束 ses-session-end.sh 自动 workspace-doctor --fix 兜底
```

- **删除时机**：PR 合入即删（自动）；会话结束兜底清理（自动）；任何时刻手查 `gov-workspace-doctor.sh`。
- **残留判定**：doctor 报 WARN 就是有脏东西，必须处理；`ses-worktree.sh clean` 手动兜底。
- **禁止**长期保留已合入分支的 worktree；分支寿命 < 1 天。

## 4. 开发与质量控制解耦

| 层 | 触发 | 内容 | 耗时 |
|----|------|------|------|
| 快层 | commit / push | 分支名、禁 master、密钥扫描、文档校验、认领归属 | 秒级 |
| 慢层 | PR / 合入 | `gov-local-ci.sh` 全绿（唯一合入权威） | 按车道 |

写代码时唯一要记：认领 → 写码 → 跑相关单测 → 提交推送。**不要为流程停下编码。**

## 5. 风险车道（local-ci 自动判定，`GOV_LANE=auto`）

| 车道 | 触发面 | 门禁 |
|------|--------|------|
| A | `docs/ design/ tests/ .ai/` 等非构建路径 | 不跑 gradle：文档/密钥/schema/test-guard |
| B | `app/src/**` | `:app:testDebugUnitTest` + 其余五步 |
| C | `domain/ application/ infrastructure/ golden/ schemas/`、构建文件、未知路径 | 全量 `gradlew build` |

混合取最高；`GOV_LANE=full` 强制全量；检测失败按 C。

## 6. 共享文件单一写者

**只允许合并队列/收尾自动化写**，功能分支一律不碰：

- `.ai/tasks.json`、`docs/ai/lifecycle/03_TaskGraph.md`（计数不手写）、`STATUS.md`
- `tests/android-test-registry.yaml`、`docs/ai/lifecycle/06_Metrics.md`

功能分支只提交：**自己的代码 + 自己的测试**。共享文件由 `gov-merge.sh` 在合入后统一同步。

## 7. 合入（唯一路径）

```bash
bash scripts/governance/gov-merge.sh <PR号>          # 完整合入 + 清理
bash scripts/governance/gov-merge.sh <PR号> --dry-run
```

内部顺序：拉 master → 冲突则停（交人）→ 派生物同步 → `gov-local-ci` → gh merge → 删分支/worktree。
PR 必须勾选 `.github/PULL_REQUEST_TEMPLATE.md` 门禁清单。

## 8. 完成定义（DoD 机械校验）

声称"任务完成"前必须：

```bash
bash scripts/governance/gov-task-done.sh T-xxx
```

它逐条执行：`target_files` 存在性 → 原型 `--strict` → `acceptance.verify_commands`。
不通过 = 未完成，不得开 PR。

## 9. 会话结束（自动 + 人工内容）

```bash
bash scripts/session/ses-session-end.sh
```

自动：workspace doctor --fix（清 worktree/分支/锁/旧日志）、派生物一致性检查。
人工：交接内容必须写清 **完成 / 未完成 / 决策理由 / 坑**（内容优先，不计时）。
墙钟门禁（inbox 48h / session-end 24h）已退役（DEC-064）。

## 10. 规则 → 执行者映射

| 规则 | 执行者（机械） | 自查命令 |
|------|----------------|----------|
| 禁止直提 master | pre-commit | `git branch --show-current` |
| 分支命名 | pre-push | `git branch --show-current` |
| 任务认领互斥 | `wf-claim.sh` + pre-commit（命中他人锁拒绝） | `wf-claim.sh status` |
| 提交归属声明 | `ses-claim-staged.sh` + pre-commit | 提交成功即通过 |
| 密钥扫描 | pre-push + local-ci + CI | `gov-secrets-scan.sh` |
| 编码 / 单测 | local-ci 车道 B/C | `gov-local-ci.sh` |
| 文档一致性 | `gov-validate`（pre-push + local-ci） | `gov-validate.sh` |
| 原型文案一致 | `gov-prototype-verify --strict`（任务 DoD） | `gov-task-done.sh T-xxx` |
| 黄金向量 / Room schema | `gov-schema-guard`（local-ci + CI） | `gov-local-ci.sh` |
| AC 完成定义 | `gov-task-done.sh` | 同上 |
| 任务归属（分支名不作数） | 提交 trailer `Task: T-XXX`（prepare-commit-msg 自动注入） | `git log -1 --format=%B` |
| 完成证据（谁/哪个提交/跑了什么） | `gov-task-done` 写 `.ai/evidence/<ID>.json`，队列据此回填 | `cat .ai/evidence/T-xxx.json` |
| 无障碍（a11y 名 / 固定高度裁剪 / 对比度） | `gov-a11y-check`（local-ci 全车道） | `python3 scripts/governance/gov-a11y-check.py` |
| 预览覆盖（每个对外屏有 @Preview） | `gov-validate` → `check_previews` | `python3 scripts/governance/gov-preview-check.py` |
| 屏可达性（点不到的屏不得新增） | `gov-reachability-check`（local-ci 全车道，棘轮基线） | `python3 scripts/governance/gov-reachability-check.py` |
| 钩子生效（hooksPath 指向版本化目录） | `gov-hook-doctor`（local-ci 全车道） | `bash scripts/governance/gov-hook-doctor.sh` |
| 工作区卫生（worktree/分支/锁） | `gov-workspace-doctor`（会话结束 + 手动） | `gov-workspace-doctor.sh` |
| 派生数据不手写 | 合并队列（单一写者） | `gov-board-sync.sh --dry` |
| 用户路径可走通 | **手动/预发**：`e2e_all.sh`（需设备，不进 local-ci） | `bash scripts/testing/e2e_all.sh` |
| 慢命令不阻塞 | **约定**（见 §2；靠纪律，无强制） | — |

> 标"约定"的规则没有机械执行者——不要假装它被强制。

## 11. 硬规则（禁止）

1. 禁止在 `master`/`main` 直提交。
2. 禁止 `--no-verify`（除非人类明确要求）。
3. 禁止未认领即开工同一任务；禁止重复实现已有功能。
4. 禁止功能分支修改共享文件（§6）。
5. 禁止前台跑 >10 秒的阻塞命令（§2）。
6. 禁止用 `git push --force` 覆盖他人分支。
7. 提交格式 `<type>(<scope>): <subject>`（英文，≤72）。

## 12. 允许写入的文档位置

- `docs/guides/GC-{slug}.md`、`docs/reference/REF-{slug}.md`、`docs/archive/ARCH-{slug}.md`
- `docs/notes/NOTE-YYYYMMDD-NNN.md`、`docs/ai/decisions/DEC-XXX.md`
- `docs/ai/lifecycle/{01..06}_*.md`（覆盖型）、`docs/ai/reviews/...`、`docs/ai/inbox/...`
- 各目录 `index.md` 可更新导航；**不可**直改黄金标准正文（须 promote）

## 13. 命令入口

```bash
# 流程
bash scripts/workflow/wf-claim.sh claim T-xxx      # 认领（原子锁）
bash scripts/workflow/wf-parallel.sh plan          # 可并行批次
bash scripts/workflow/wf-run.sh start <name> -- <cmd>   # 后台执行
bash scripts/session/ses-worktree.sh add|clean|list
bash scripts/governance/gov-workspace-doctor.sh --fix
bash scripts/governance/gov-task-done.sh T-xxx
bash scripts/governance/gov-merge.sh <PR>
bash scripts/governance/gov-local-ci.sh            # 合入门禁（慢，用 wf-run 后台）
bash scripts/governance/gov-validate.sh            # 文档/协议校验（秒级）
python3 scripts/governance/gov-a11y-check.py       # 无障碍静态检查（秒级）
python3 scripts/governance/gov-preview-check.py    # 预览覆盖检查（秒级）
python3 scripts/governance/gov-reachability-check.py  # 屏可达性审计（秒级，基线只减不增）
bash scripts/governance/gov-hook-doctor.sh         # 钩子配置检查（hooksPath + 包装器）
bash scripts/testing/e2e_all.sh                   # 用户路径走查（需设备；唯一能证明点得通）
bash scripts/governance/gov-board-sync.sh          # 派生物同步（合并队列内部调用）
```

## 14. 本机钩子与 CI（事实）

| 层 | 行为 |
|----|------|
| pre-commit | 多会话守卫（共享主树+并发→强制 worktree）、禁 master、归属声明、认领锁拦截、源码时 compile + custom-checks |
| pre-push | 分支名 + 文档校验（QUICK，不跑 pytest）+ 密钥扫描 |
| local-ci | 合入权威：按车道 A/B/C 跑 gradle + 六项校验，证据写 `.cache/local-ci-last.json` |
| Actions | `ci.yml` 覆盖特性分支与 PR；**账单问题期间不可用**，合入以 local-ci 为准 |
| 分支保护 | 无（人类不做 Pro）；靠 local-ci + PR 模板纪律 |
| 任务板权威 | `.ai/tasks.json` |

---

## 附：本文档的维护规则

- 新增门禁必须写进 §10 映射表（规则 → 执行者 → 自查命令），否则视为"约定"。
- 规则生命周期：DEC 写清 **保护指标 / 预期成本 / 复审条件**；两次误伤或 30 天零命中 → 退役评审（DEC-064）。
- 本文件保持精炼：细节进 `docs/guides/GC-*.md`。
