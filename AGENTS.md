# AI 协作协议（操作手册）

> 权威任务板：`.ai/tasks.json`。Notion 页面与仓库路径的对照见 `docs/reference/REF-notion-index.md`；轻衡健康下新增页面时同步更新该文件。本文件是**索引**，不是规则本体：每条规则都必须能指到一个机械执行者。
> 找不到执行者的条目，一律归类为"约定"，不假装是门禁。

---

## 0. 开工：三条命令，顺序固定

```bash
bash scripts/session/ses-session-begin.sh        # 会话 ID + 上下文 + 合规卡
bash scripts/workflow/wf-role.sh claim <role>    # dev | gov | docs | review
bash scripts/workflow/wf-autopilot.sh status     # 一屏真相 + NEXT 命令
```

- 一个会话只持有一个角色，8 小时过期。角色决定"能写哪里 + 必须交什么"，不决定门禁深度。
- 未声明角色不得改代码或治理脚本：pre-commit 直接拒绝。临时放宽 `GOV_ROLE_REQUIRED=0`（会打印，评审可见）。
- 其它 AI 工具读的是生成物：`.cursorrules`、`.clinerules`、`.opencode/rules.md`；由 `gov-sync-agent-rules.sh` 生成，手改会被 `gov-validate` 判红。

## 0.1 治理总开关

```bash
bash scripts/governance/gov-mode.sh off 2h   # 跳过本地快层门禁（不豁免 gradlew/pytest/gov-local-ci）
bash scripts/governance/gov-mode.sh on
bash scripts/governance/gov-mode.sh status
```

---

## 1. 三环执行模型（快慢分离）

| 环 | 预算 | 命令 | 跑什么 | 不跑什么 |
|---|---|---|---|---|
| 内环 | ≤ 5s | `python3 scripts/governance/entrypoints/gov-validate --tags fast` | 6 条 fast 规则（emoji/hygiene/content/structure/scripts） | git、编译、全量扫描 |
| 交付 | ≤ 5s | `scripts/dev/ship.sh -m "..." <path>` | 暂存 + 提交 + 推送 + PR | 全量测试、证据生成 |
| 提交环 | ≤ 3s | `git commit` | 分支/范围/角色/`bash -n`/增量编译 | pytest、可达性、原型、全量 |
| 批次环 | ≤ 10min | `bash scripts/governance/gov-local-ci.sh` | 按车道决定：lane-A 仅静态 / lane-B :app:testDebugUnitTest / lane-C 全量 gradle + pytest | — |

> 内环、交付、提交环**只增量**，批次环**按车道**全量。>10s 命令必须后台（`wf-run.sh`）。

---

## 2. 画像与角色（单一真源：`.ai/roles.json`）

| 画像 | 允许改动 | 门禁标签 | 说明 |
|---|---|---|---|
| `docs` | `docs/ design/ *.md` | `lane-a` | 不构建不测试 |
| `test` | `tests/ **/src/test/**` | `lane-b` | 全量 pytest |
| `code` | `app/ domain/ application/ infrastructure/` + 构建文件 | `lane-b` / `lane-c` | gradle + 受影响 pytest |
| `gov` | `scripts/** tests/** docs/** .github/` + 根规则 | `lane-c` | 全量 pytest + 静态 |
| `mixed` | 任意（兜底） | `lane-c` | 最严 |

严格度：`docs < test < code < gov < mixed`。只能升不能降，强制降级需 `GOV_PROFILE_FORCE=1`。

**车道优先级**：`CORE(4) > C(3) > B(2) > A(1)`，混合变更取最高。

**车道优先级**：`CORE(4) > C(3) > B(2) > A(1)`，混合变更取最高。

| 角色 | 可写 | 必须产出 |
|---|---|---|
| `dev` | `app/** domain/** application/** infrastructure/**` + 构建 | 任务证据 + 受影响测试通过 |
| `test` | `tests/**` 与各模块 `src/test/**` | 失败复现或覆盖率证据 |
| `gov` | `scripts/** tests/** docs/** .github/**` + 根规则 | 全量 pytest 通过 |
| `docs` | `docs/** design/** *.md` | 索引收录 + 链接可达 |
| `review` | **只读**，仅 `docs/ai/reviews/**` | 评审报告（署名 ≠ 认领者） |

---

## 3. 标签驱动的规则集（单一定义源：`orchestrator/registry.py`）

| 标签 | 含义 | 典型入口 | 规则数 |
|---|---|---|---|
| `fast` | ≤1s，预提交 | `pre-commit` | 6 |
| `push` | ≤5s，推送前 | `pre-push` | 22 |
| `lane-a` | 非构建路径 | `gov-local-ci` 车道 A | 22 |
| `lane-b` | 仅 app 模块 | `gov-local-ci` 车道 B | 27 |
| `lane-c` | 全量构建路径 | `gov-local-ci` 车道 C | 27 |
| `core` | 后端核心层 | `gov-local-ci` 车道 CORE | 30 |
| `dod` | 完成定义 | `gov-task-done` | 26 |
| `a11y` | 无障碍 | `dod` 子集 | — |
| `reach` | 可达性 | `dod` 子集 | — |
| `schema` | Room schema/黄金向量 | `dod` 子集 | — |
| `secrets` | 密钥扫描 | `push` + 所有 lane | — |
| `proto` | 原型文案 | `gov-prototype-verify` | — |

**入口只加载匹配标签的规则**，绝不全量跑。

---

## 4. 关键命令入口（新架构）

```bash
# 静态校验（统一入口，标签决定跑什么）
python3 scripts/governance/entrypoints/gov-validate --tags fast|push|lane-a|lane-b|lane-c|dod [--fix] [--json]

# 钩子（已安装到 .git/hooks/）
pre-commit          # 等价 --tags fast
pre-push            # 等价 --tags push + 分支名校验
commit-msg          # 格式 <type>(<scope>): <subject>

# 批次环（合并前唯一全量点）
bash scripts/governance/gov-local-ci.sh [--strict] [GOV_LANE=A|B|C]

# 任务/合并
bash scripts/workflow/wf-claim.sh claim T-xxx
bash scripts/governance/gov-merge.sh <PR号>
bash scripts/governance/gov-task-done.sh T-xxx

# 工作区卫生
bash scripts/governance/gov-workspace-doctor.sh --fix
bash scripts/session/ses-session-end.sh
```

---

## 5. 硬规则（禁止）

1. 禁止在 `master`/`main` 直提交。
2. 禁止 `--no-verify`（可发现：`gov-audit-bypass.sh`；阻断：`GOV_BYPASS_POLICY=block`）。
3. 禁止未认领即开工同一任务；禁止重复实现。
4. 禁止功能分支修改共享文件（`.ai/tasks.json`、`STATUS.md`、`android-test-registry.yaml`、`03_TaskGraph.md`、`06_Metrics.md`、`docs/index.md`、`AGENTS.md`）。
5. 禁止前台跑 >10s 命令（用 `wf-run.sh` 后台）。
6. 禁止 `git push --force` 覆盖他人分支。
7. 提交格式 `<type>(<scope>): <subject>`（英文 ≤72）。
8. 禁止未声明角色改代码/治理脚本。
9. 禁止保留备份残留（`.bak`/`.orig`/`.rej`/`~`/`副本`）。

---

## 6. 规则 → 执行者映射（仅列机械执行者）

| 规则 | 执行者 | 自查命令 |
|---|---|---|
| 禁止直提 master | pre-commit | `git branch --show-current` |
| 分支命名 | pre-push | 同上 |
| 任务认领互斥 | `wf-claim.sh` + pre-commit | `wf-claim.sh status` |
| 角色写入边界 | `wf-role.sh` + pre-commit | `wf-role.sh show` |
| 任务画像 | `gov-profile.sh` + pre-commit + local-ci | `gov-profile.sh explain` |
| AI 规则一致 | `gov-sync-agent-rules.sh --check` | 同左 |
| 提交归属 | `ses-claim-staged.sh` + pre-commit | 提交即通过 |
| 密钥扫描 | `gov-secrets-scan` (push/lane-*) | `bash scripts/governance/gov-secrets-scan.sh` |
| 编码/单测 | local-ci 车道 B/C | `bash scripts/governance/gov-local-ci.sh` |
| 测试增量/分片 | `gov-affected.sh` / `gov-pytest.sh` | `gov-affected.sh --json` |
| 文档一致性 | `gov-validate` (push/lane-a) | `python3 scripts/governance/entrypoints/gov-validate --tags push` |
| 原型文案一致 | `gov-prototype-verify --strict` (dod) | `bash scripts/governance/gov-task-done.sh T-xxx` |
| 黄金向量/Room schema | `gov-schema-guard` (dod) | 同上 |
| AC 完成定义 | `gov-task-done.sh` | 同上 |
| 任务归属 | 提交 trailer `Task: T-XXX` | `git log -1 --format=%B` |
| 完成证据 | `gov-task-done` 写 / 队列读 | `ls .ai/evidence` |
| 无障碍/可达性/预览 | `a11y`/`reach`/`proto` (dod) | 对应标签入口 |
| 钩子生效 | `gov-hook-doctor` (local-ci) | `bash scripts/governance/gov-hook-doctor.sh` |
| 绕过审计 | `gov-audit-bypass.sh` (pre-push) | `GOV_BYPASS_POLICY=block git push` |
| 工作区卫生 | `gov-workspace-doctor` (会话结束) | `gov-workspace-doctor.sh` |
| 备份残留清理 | `GOV-HYG-001` (fast) | 包含在 fast 标签 |
| 派生数据不手写 | 合并队列（单一写者） | `gov-board-sync.sh --dry` |
| 用户路径可走通 | `e2e_all.sh`（手动/预发） | `bash scripts/testing/e2e_all.sh` |
| 内环预算 | `dev-loop.sh --budget` | 输出自带耗时 |

---

## 7. 允许写入的文档位置

- `docs/guides/GC-{slug}.md`、`docs/reference/REF-{slug}.md`、`docs/archive/ARCH-{slug}.md`
- `docs/notes/NOTE-YYYYMMDD-NNN.md`、`docs/ai/decisions/DEC-XXX.md`
- `docs/ai/lifecycle/{01..06}_*.md`（覆盖型）、`docs/ai/reviews/...`、`docs/ai/inbox/...`
- 各目录 `index.md` 可更新导航；**不可**直改黄金标准正文（须 promote）。

---

## 8. 本机钩子与 CI 现状

| 层 | 行为 |
|---|---|
| pre-commit | `fast` 标签 6 条规则（~17ms），无编译 |
| pre-push | `push` 标签 22 条规则（~862ms）+ 分支名校验 |
| local-ci | **合入权威**：按 `gov-ci-scope.sh` 判定车道 → 对应标签规则 + gradle + pytest |
| 队列 | 唯一写共享状态与 master 的自动化；合入前强制 `GOV_CI_FULL=1` |
| Actions | `ci.yml` 覆盖特性分支与 PR；**账单问题期间不可用**，以 local-ci 为准 |
| 分支保护 | 无（不做 Pro），靠 local-ci + PR 模板纪律 |

---

## 9. 已知缺口与退役条件

| 缺口 | 证据 | 下一步 |
|---|---|---|
| 任务板与真实工作脱节 | 21 条 pending 缺 AC，4 条 blocked (T-132~T-135 需 DEC-081) | `wf-board-audit.sh` |
| 证据需手动提交 | `.ai/evidence/` 未纳入自动提交 | 由 `dev-loop`/`finish` 自动 `git add` 证据 |
| 无远程权威 | Actions 不可用、无分支保护 | 自建 bare remote + pre-receive，或恢复 required checks |
| `--no-verify` 只能发现不能阻止 | 审计默认 warn | `GOV_BYPASS_POLICY=block`，或服务端强制 |
| 多 AI 评审停更 | `docs/ai/reviews/` 最新 10-22 | 用 `review` 角色在关键节点重跑一次 |
| Notion MCP 凭据虚设 | `.opencape/mcp.json` 有占位符 | 补入真实 API Key + workspace_id |
| 7 个 prd_ref 未入索引 | REF-notion-index.md 末待确认 | 确认 Notion 页面标题后补充 |

> **规则生命周期**：新增门禁必须同时给出执行者、负向测试、退役条件（两次误伤或 30 天零命中即进入退役评审）。没有执行者的条目只能写进"约定"，不得写进本表。

---

## 10. 本文档维护规则

- 新增门禁必须写进 §6 映射表，否则视为"约定"。
- 本文件属于共享文件（§5 共享文件清单）：单独开一支改，改完运行 `gov-sync-agent-rules.sh`。
- 本文件保持精炼：细节进 `docs/guides/GC-*.md` 与 `docs/ai/decisions/DEC-*.md`。
- **删减优于新增**：过时机制直接删，不留痕迹。