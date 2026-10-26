# AI 协作协议（操作手册）

> 权威任务板：`.ai/tasks.json`。本文件是**索引**，不是规则本体：每条规则都必须能指到一个机械执行者。
> 找不到执行者的条目，一律归类为"约定"，不假装是门禁。

---

## 0. 开工：三条命令，顺序固定

```bash
bash scripts/session/ses-session-begin.sh        # 会话 ID + 上下文 + 合规卡
bash scripts/workflow/wf-role.sh claim <role>    # dev | gov | docs | review
bash scripts/workflow/wf-autopilot.sh status     # 一屏真相 + NEXT 命令
```

- **一个会话只持有一个角色**，8 小时过期。角色决定"能写哪里 + 必须交什么"，不决定门禁深度。
- **未声明角色不得改代码或治理脚本**：pre-commit 直接拒绝。临时放宽用 `GOV_ROLE_REQUIRED=0`（会打印，评审可见）。
- 其它 AI 工具（Cursor / Cline / OpenCode）读的是生成物，见 `.cursorrules`、`.clinerules`、`.opencode/rules.md`；三者由 `gov-sync-agent-rules.sh` 生成，手改会被 `gov-validate` 判红。

## 0.1 治理总开关（写代码前先看这条）

```bash
bash scripts/governance/gov-mode.sh off 2h   # 纯写码：跳过本地快层门禁
bash scripts/governance/gov-mode.sh on
bash scripts/governance/gov-mode.sh status
```

- 作用域为单个 worktree，默认 2h、上限 8h，到期自动恢复。
- 每次跳过写 `.ai/gov-skips/YYYY-MM-DD.log`，commit/push 时打印一行提示（`GOV_QUIET=1` 静音）。
- **不豁免**：`gradlew`、`pytest`、`gov-local-ci`。开关只关"快层"，不关"验证"。

## 0.5 并发三件套（多 AI 同时写代码时）

```bash
scripts/dev/who-is-working.sh    # 谁在哪个 worktree、改了哪些模块
scripts/dev/dev-verify.sh        # 编译 → 装机 → 启动（+ --journey 走一条路径）
scripts/dev/merge-mine.sh        # rebase origin/master → 推送（撞车自动重试）
```

- `TOUCHED` 重叠先谈归属；共享文件只由一人改（见 §5）。
- 原型换代是**替换**：同一提交删旧实现并登记 `scripts/dev/superseded.txt`。

## 1. 开工决策表

| 你要做的事 | 角色 | 画像 | 动作 |
|---|---|---|---|
| 写功能 / 修 bug | `dev` | code | claim → worktree → 写码 + 同包测试 → commit → PR → 队列 |
| 改治理脚本 / 钩子 / CI | `gov` | gov | claim → 直接改 → 全量 pytest → commit |
| 只改文档 / 决策 / 原型 | `docs` | docs | claim → 直接改 → validate → commit |
| 评审别人/别的会话的产出 | `review` | docs | 另开会话 → 只读 → 写 `docs/ai/reviews/` → `verify-review` |
| 合入别人的 PR | — | — | `bash scripts/governance/gov-merge.sh <PR号>` |
| 收尾 | — | — | `bash scripts/session/ses-session-end.sh` |
| 看哪些任务能并行 | — | — | `bash scripts/workflow/wf-parallel.sh plan` |

认领失败（锁被他人持有）→ **换任务，不要重复实现**。

## 2. 三环执行模型（快慢分离）

| 环 | 预算 | 命令 | 跑什么 | 不跑什么 |
|---|---|---|---|---|
| 内环 | ≤ 5s | `scripts/dev/dev-loop.sh [--watch]` | 增量编译 + 同名 Kotlin 测试 + 受影响 pytest | git、证据、全量扫描 |
| 交付 | ≤ 5s | `scripts/dev/ship.sh -m "..." <path>` | 暂存 + 提交 + 推送 + PR；全量验证放后台 | 全量测试、证据生成 |
| 提交环 | ≤ 3s | `git commit` | 分支/范围/角色/`bash -n`/暂存区 code-check/增量编译 | pytest、可达性、原型、全量 |
| 批次环 | ≤ 10min | `bash scripts/governance/gov-local-ci.sh` | 全量测试（分片）+ 静态检查（内容哈希缓存）+ gradle | — |

铁律：

1. **全量验证只发生在合入**（`GOV_CI_FULL=1`），其余环节一律增量。
2. > 10 秒的命令不得前台阻塞：`wf-run.sh start <name> -- <cmd>`，需要结果时 `wf-run.sh wait <id>`。
3. 内环超预算就停：`dev-loop.sh --budget 5` 会把剩余验证交给批次环，不偷偷跑 30 秒。

## 3. 任务画像与角色

**画像决定"跑多深"**（由认领声明 + 实际改动路径合并，**只能升不能降**）：

| 画像 | 允许改动 | 提交环 | 批次环 |
|---|---|---|---|
| docs | `docs/ design/ *.md` | 范围 + 语法 | 车道 A，不构建不测试 |
| test | `tests/ app/src/test/` | 范围 + 语法 | 全量 pytest |
| code | `app/ domain/ application/ infrastructure/` + 构建文件 | 增量编译 + 暂存区检查 | gradle + 受影响 pytest |
| gov | `scripts/ tests/ docs/ .github/` | 暂存区检查 + 语法 | 全量 pytest + 静态 |
| mixed | 任意（兜底，最严） | 并集 | 全量 |

严格度：`docs < test < code < gov < mixed`。降级只能通过 `GOV_PROFILE_FORCE=1 GOV_PROFILE=<p>` 显式进行。

**角色决定"能不能写"**（`.ai/roles.json` 单一真源）：

| 角色 | 可写 | 必须产出 |
|---|---|---|
| `dev` | `app/** domain/** application/** infrastructure/**` + 构建文件 | 任务证据 + 受影响测试通过 |
| `test` | 仅 `tests/**` 与各模块 `src/test/**` | 失败复现或覆盖率证据 |
| `gov` | `scripts/** tests/** docs/** .github/**` + 根规则文件 | 全量 pytest 通过 |
| `docs` | `docs/** design/** *.md` | 索引收录 + 链接可达 |
| `review` | **只读**，仅 `docs/ai/reviews/**` | 评审报告（署名会话，且 ≠ 认领者） |

`review` 不能由持有任务锁的会话认领——自审在机制上不可能。

## 4. worktree 生命周期（状态机）

```
认领 ──► ses-worktree.sh add <type>/<topic>
          │
          ▼
      cd 到该树 → 编码/提交/推送
          │
          ▼
      PR 合入 → gov-merge.sh 自动删 worktree + 分支
          │
          ▼
      会话结束 → ses-session-end.sh 自动 workspace-doctor --fix
```

- 上限 3 个（`WF_WORKTREE_LIMIT` 可调），超限拒绝创建并打印清理命令。
- 判定：`gov-workspace-doctor.sh` 报 WARN 就是有脏东西；`ses-worktree.sh clean` 手动兜底。
- 禁止长期保留已合入分支的 worktree；分支寿命 < 1 天。

## 5. 共享文件单一写者

只允许合并队列/收尾自动化写，功能分支一律不碰：

- `.ai/tasks.json`、`docs/ai/lifecycle/03_TaskGraph.md`、`STATUS.md`
- `tests/android-test-registry.yaml`、`docs/ai/lifecycle/06_Metrics.md`、`docs/index.md`
- `AGENTS.md`（协议本体：需要改动时单独开一支，并在 PR 里说明）

功能分支只提交：**自己的代码 + 自己的测试 + 自己的文档**。

## 6. 合入（唯一路径）

```bash
bash scripts/governance/gov-merge.sh <PR号>          # 完整合入 + 清理
bash scripts/governance/gov-merge.sh <PR号> --dry-run
bash scripts/workflow/wf-queue.sh run                # 批次：N 分支 → 1 候选 → 1 次验证
```

内部顺序：拉 master → 冲突则停（交人）→ 派生物同步 → `gov-local-ci`（全量）→ gh merge → 删分支/worktree。

## 7. 完成定义（DoD）

```bash
bash scripts/governance/gov-task-done.sh T-xxx
```

它逐条执行：`target_files` 存在性 → 原型 `--strict` → `acceptance.verify_commands`，并把结果（含每条 check 与 SHA）写进 `.ai/evidence/<ID>.json`。

- **通过与否都写**：失败证据的 `ok=false`，用于回答"卡在哪一步"。
- **证据要随分支提交**：队列在候选树里读它，不提交就等于没做。
- **队列会拒绝**：带 `Task: T-xxx` trailer 的分支缺证据或 `ok!=true` 时，合入被拒（`GOV_ALLOW_MISSING_EVIDENCE=1` 可放宽，会打印）。

## 8. 会话结束

```bash
bash scripts/session/ses-session-end.sh
```

自动：workspace doctor --fix（清 worktree/分支/锁）、派生物一致性检查。
人工：交接必须写清 **完成 / 未完成 / 决策理由 / 坑**（内容优先，不计时）。

## 9. 硬规则（禁止）

1. 禁止在 `master`/`main` 直提交。
2. 禁止 `--no-verify`（本地可发现：`gov-audit-bypass.sh`；需要阻断时 `GOV_BYPASS_POLICY=block`）。
3. 禁止未认领即开工同一任务；禁止重复实现已有功能。
4. 禁止功能分支修改共享文件（§5）。
5. 禁止前台跑 > 10 秒的阻塞命令（§2）。
6. 禁止用 `git push --force` 覆盖他人分支。
7. 提交格式 `<type>(<scope>): <subject>`（英文，≤72）。
8. 禁止未声明角色就改代码或治理脚本（`GOV_ROLE_REQUIRED`）。

## 10. 规则 → 执行者映射

| 规则 | 执行者（机械） | 自查命令 |
|---|---|---|
| 禁止直提 master | pre-commit | `git branch --show-current` |
| 分支命名 | pre-push | 同上 |
| 任务认领互斥 | `wf-claim.sh` + pre-commit | `wf-claim.sh status` |
| **角色写入边界** | `wf-role.sh` + pre-commit | `wf-role.sh show` |
| **任务画像** | `gov-profile.sh` + pre-commit + local-ci | `gov-profile.sh explain` |
| **AI 规则一致** | `gov-sync-agent-rules.sh --check`（validate） | 同左 |
| 提交归属声明 | `ses-claim-staged.sh` + pre-commit | 提交成功即通过 |
| 密钥扫描 | pre-push + local-ci + CI | `gov-secrets-scan.sh` |
| 编码 / 单测 | local-ci 车道 B/C | `gov-local-ci.sh` |
| **测试增量与分片** | `gov-affected.sh` / `gov-pytest.sh` | `gov-affected.sh --json` |
| 文档一致性 | `gov-validate`（pre-push + local-ci） | `gov-validate.sh` |
| 原型文案一致 | `gov-prototype-verify --strict`（DoD） | `gov-task-done.sh T-xxx` |
| 黄金向量 / Room schema | `gov-schema-guard`（local-ci + CI） | `gov-local-ci.sh` |
| AC 完成定义 | `gov-task-done.sh` | 同上 |
| 任务归属 | 提交 trailer `Task: T-XXX` | `git log -1 --format=%B` |
| **完成证据** | `gov-task-done` 写 / `gov-evidence-check` + 队列读，缺即拒绝 | `ls .ai/evidence` |
| 无障碍 / 预览 / 屏可达 | `gov-a11y-check` / `gov-preview-check` / `gov-reachability-check` | 对应脚本 |
| 钩子生效 | `gov-hook-doctor`（local-ci） | `bash scripts/governance/gov-hook-doctor.sh` |
| **绕过审计** | `gov-audit-bypass.sh`（pre-push） | `GOV_BYPASS_POLICY=block git push` |
| 工作区卫生 | `gov-workspace-doctor`（会话结束） | `gov-workspace-doctor.sh` |
| 派生数据不手写 | 合并队列（单一写者） | `gov-board-sync.sh --dry` |
| 用户路径可走通 | **手动/预发**：`e2e_all.sh`（需设备） | `bash scripts/testing/e2e_all.sh` |
| **内环预算** | `dev-loop.sh --budget`（约定 + 脚本） | 输出自带耗时 |

## 11. 允许写入的文档位置

- `docs/guides/GC-{slug}.md`、`docs/reference/REF-{slug}.md`、`docs/archive/ARCH-{slug}.md`
- `docs/notes/NOTE-YYYYMMDD-NNN.md`、`docs/ai/decisions/DEC-XXX.md`
- `docs/ai/lifecycle/{01..06}_*.md`（覆盖型）、`docs/ai/reviews/...`、`docs/ai/inbox/...`
- 各目录 `index.md` 可更新导航；**不可**直改黄金标准正文（须 promote）

## 12. 命令入口

```bash
# 流程
bash scripts/workflow/wf-autopilot.sh status          # 一屏真相 + NEXT
bash scripts/workflow/wf-claim.sh claim T-xxx         # 认领（原子锁）
bash scripts/workflow/wf-role.sh claim dev|gov|docs|review
bash scripts/workflow/wf-parallel.sh plan             # 可并行批次
bash scripts/workflow/wf-board-audit.sh               # 任务板审计（重复/分组/依赖环）
bash scripts/workflow/wf-task-board.sh ready          # 可开工任务（依赖已满足）
bash scripts/workflow/wf-task-board.sh blocked        # 被谁阻塞
bash scripts/workflow/wf-task-board.sh checkpoint T-xxx "进度/决定/下一步"
bash scripts/workflow/wf-batch.sh list                # 批次进度
bash scripts/workflow/wf-batch.sh start B-w1-screens   # 按角色切模式 + 打印测试计划
bash scripts/workflow/wf-run.sh start <name> -- <cmd> # 后台执行
bash scripts/workflow/wf-queue.sh run                 # 批次合入（唯一全量点）

# 开发
scripts/dev/dev-loop.sh --watch                       # 内环（≤5s 预算）
scripts/dev/dev-verify.sh                             # 编译 → 装机 → 启动
bash scripts/testing/gov-pytest.sh --all              # 全量测试（分片 + 缓存）
bash scripts/governance/gov-affected.sh --run         # 只跑受影响测试

# 门禁
bash scripts/governance/gov-validate.sh               # 静态校验（0.4s）
bash scripts/governance/gov-local-ci.sh               # 批次环
bash scripts/governance/gov-task-done.sh T-xxx        # DoD
bash scripts/governance/gov-merge.sh <PR>
bash scripts/governance/gov-workspace-doctor.sh --fix
bash scripts/governance/gov-sync-agent-rules.sh --check
```

## 13. 本机钩子与 CI

| 层 | 行为 |
|---|---|
| pre-commit | 多会话守卫 → 禁 master → 归属 → 画像范围 → 角色范围 → `bash -n` → 增量编译 → 暂存区 code-check → 记录钩子轨迹 |
| pre-push | 分支名 → 文档校验（QUICK）→ 密钥扫描 → `--no-verify` 审计（默认 warn） |
| local-ci | 合入权威：车道 + 画像 → 分片全量测试 + 静态检查（缓存）→ 写 `.cache/local-ci-last.json` |
| 队列 | 唯一写共享状态与 master 的自动化；合入前强制 `GOV_CI_FULL=1` |
| Actions | `ci.yml` 覆盖特性分支与 PR；**账单问题期间不可用**，以 local-ci 为准 |
| 分支保护 | 无（不做 Pro），靠 local-ci + PR 模板纪律 |

## 14. 已知缺口与退役条件

| 缺口 | 证据 | 下一步 |
|---|---|---|
| 任务板与真实工作脱节 | 83 条中 23 条无 `target_files`，12 条 pending 仍 `draft` | `wf-board-audit.sh`，逐条补 AC 或标记 blocked |
| 证据需手动提交 | `.ai/evidence/` 未纳入自动提交 | 由 `dev-loop`/`finish` 自动 `git add` 证据 |
| 无远程权威 | Actions 不可用、无分支保护 | 自建 bare remote + pre-receive，或恢复 required checks |
| `--no-verify` 只能发现不能阻止 | 审计默认 warn | `GOV_BYPASS_POLICY=block`，或服务端强制 |
| 多 AI 评审停更 | `docs/ai/reviews/` 最新 10-22 | 用 `review` 角色在关键节点重跑一次 |

**规则生命周期**：新增门禁必须同时给出执行者、负向测试、退役条件（两次误伤或 30 天零命中即进入退役评审，DEC-064）。没有执行者的条目只能写进"约定"，不得写进本表。

---

## 附：本文档的维护规则

- 新增门禁必须写进 §10 映射表，否则视为"约定"。
- 本文件属于共享文件（§5）：单独开一支改，改完同步运行 `gov-sync-agent-rules.sh`。
- 本文件保持精炼：细节进 `docs/guides/GC-*.md` 与 `docs/ai/decisions/DEC-*.md`。
