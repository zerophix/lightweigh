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

## 0.1 治理暂停（≠ 工作模式，别混）

```bash
bash scripts/workflow/wf-mode.sh suspend 2h    # 暂停门禁（推荐入口）
bash scripts/workflow/wf-mode.sh resume
bash scripts/governance/gov-mode.sh status     # 看暂停状态
GOV_SUSPEND=1 git commit ...                   # 只暂停这一条命令
```

- 暂停期间 pre-commit/pre-push 直接放行，**但不豁免** `gradlew`、`pytest`、`gov-local-ci`。
- 暂停用 `GOV_SUSPEND`。`GOV_MODE` 只表示工作模式（见 §0.2），两者曾是同一个变量（DEC-083）。

## 0.2 工作模式（5 档，DEC-077）

```bash
bash scripts/workflow/wf-mode.sh status                 # 当前模式 + 剩余时间
bash scripts/workflow/wf-mode.sh switch explore --duration 2h
bash scripts/workflow/wf-mode.sh switch code            # 回到标准模式
```

| 模式 | 效果 | TTL |
|---|---|---|
| `explore` | 跳过编译、pre-push audit-only、local-ci 车道 A | 2h |
| `docs` | 同上 | 2h |
| `test` | 跳过编译，只跑 pytest | 4h |
| `code` | 标准全量（默认） | 永久 |
| `gov` | 最严，local-ci 全量 | 永久 |

- 模式文件 `.ai/current-mode.json`，**HMAC 签名 + 到期自动回退 dev**。
- 宽松模式只能由 `wf-mode.sh switch` 写入。手写环境变量 `GOV_MODE=explore` 会被钩子清洗并告警。
- 合并时 `gov-merge.sh` 强制 `GOV_MODE=dev` + 车道 C + 全量 CI，explore 期间的跳过必须补票。
- 完整规范：`docs/ops/GC-mode-system.md`。

---

## 1. 四环执行模型（快慢分离）

| 环 | 预算 | 命令 | 跑什么 | 不跑什么 |
|---|---|---|---|---|
| 内环 | ≤ 5s（实测 0.4s） | `bash scripts/governance/gov-validate.sh` | 15 个静态模块：hooks/README/语言/结构/命名/编码/技能/原型/备份残留 | git 变更判定、编译、密钥、可达性 |
| 交付 | ≤ 5s | `scripts/dev/ship.sh -m "..." <path>` | 暂存 + 提交 + 推送 + PR | 全量测试、证据生成 |
| 提交环 | 含增量编译，实测 1–3 分钟 | `git commit` | 分支/并发会话/归属/任务锁/画像/角色/`bash -n`/四模块编译 | pytest、可达性、原型、全量 |
| 批次环 | ≤ 10min | `bash scripts/governance/gov-local-ci.sh` | 按车道决定：lane-A 仅静态 / lane-B :app:testDebugUnitTest / lane-C 全量 gradle + pytest | — |

> 内环与交付**只增量**，批次环**按车道**全量。>10s 命令必须后台（`wf-run.sh`）。
> `gov-validate.sh` 不接受标签参数，恒跑全部 18 个模块（实测 7.3s，见 §8）。

---

## 2. 画像与角色（单一真源：`.ai/roles.json`）

| 画像 | 允许改动 | 车道 | 说明 |
|---|---|---|---|
| `docs` | `docs/ design/ *.md` | A | 不构建不测试 |
| `test` | `tests/ **/src/test/**` | B | 全量 pytest |
| `code` | `app/ domain/ application/ infrastructure/` + 构建文件 | B / C | gradle + 受影响 pytest |
| `gov` | `scripts/** tests/** docs/** .github/` + 根规则 | C | 全量 pytest + 静态 |
| `mixed` | 任意（兜底） | C | 最严 |

严格度：`docs < test < code < gov < mixed`。只能升不能降，强制降级需 `GOV_PROFILE_FORCE=1`。

**车道优先级**：`C(3) > B(2) > A(1)`，混合变更取最高（`CORE` 车道已并入 C）。

| 角色 | 可写 | 必须产出 |
|---|---|---|
| `dev` | `app/** domain/** application/** infrastructure/**` + 构建 | 任务证据 + 受影响测试通过 |
| `test` | `tests/**` 与各模块 `src/test/**` | 失败复现或覆盖率证据 |
| `gov` | `scripts/** tests/** docs/** .github/**` + 根规则 | 全量 pytest 通过 |
| `docs` | `docs/** design/** *.md` | 索引收录 + 链接可达 |
| `review` | **只读**，仅 `docs/ai/reviews/**` | 评审报告（署名 ≠ 认领者） |

---

## 3. 门禁规则集（真实来源：`scripts/governance/gov-local-ci.sh`）

> 历史说明：标签驱动的 `orchestrator/registry.py` 已退役（该文件与 `entrypoints/` 均不存在）。现有门禁按**脚本**组织，没有 tag 抽象层。

**所有车道都跑的同 8 条静态检查**（`gov-local-ci.sh` 的 `DOC_STEPS`）：

| 检查 | 执行者 | 拦什么 |
|---|---|---|
| gov-validate | `gov-validate.sh` | 结构/命名/编码/技能/原型/备份残留 |
| gov-custom-checks | `gov-custom-checks.sh` | 项目自定义规则 |
| gov-secrets-scan | `gov-secrets-scan.sh` | 密钥泄露 |
| gov-schema-guard | `gov-schema-guard.sh` | Room schema / 黄金向量 |
| gov-test-guard | `gov-test-guard.sh` | 改 main 却没有同模块测试 |
| gov-a11y-check | `gov-a11y-check.py` | 无障碍 A11Y-001/002/003 |
| gov-reachability-check | `gov-reachability-check.py` | 可达性基线 |
| gov-hook-doctor | `gov-hook-doctor.sh` | 钩子是否真的生效 |

**gradle 按车道**：

| 车道 | gradle | 判定者 |
|---|---|---|
| A | 无 | `gov-ci-scope.sh`：变更不涉及构建，或 `GOV_MODE=explore/docs` |
| B | `:app:testDebugUnitTest` | 变更集中在 `app/src/` |
| C | `test`（`GOV_CI_PROFILE=build` → `build`） | 全量 / 核心层 / 混合变更 |

`gov-task-done.sh` 另跑完成定义（a11y/可达性/schema/原型文案），是唯一比 local-ci 更严的入口。

---

## 4. 关键命令入口

```bash
# 静态校验（全量静态模块，实测 0.4s）
bash scripts/governance/gov-validate.sh [--fix]

# 钩子（core.hooksPath=scripts/hooks，版本化，不往 .git/hooks 拷贝副本）
scripts/hooks/pre-commit    # 分支/角色/画像/模式/HMAC/bash -n/增量编译
scripts/hooks/pre-push      # 分支名/文档/密钥/绕过审计
scripts/hooks/commit-msg    # <type>(<scope>): <subject>
scripts/hooks/prepare-commit-msg  # 注入 Session-Id + Task: T-xxx trailer

# 批次环（合并前唯一全量点）
GOV_LANE=A|B|C|full bash scripts/governance/gov-local-ci.sh [--strict]

# 任务/合并
bash scripts/workflow/wf-claim.sh claim T-xxx
bash scripts/governance/gov-merge.sh <PR号>
bash scripts/governance/gov-task-done.sh T-xxx

# 批次 Worktree (DEC-082)
bash scripts/session/ses-worktree.sh batch-add B-xxx [--in-repo]   # 创建/复用批次 worktree
bash scripts/workflow/wf-claim.sh claim T-xxx --batch B-xxx       # 认领批次内任务
bash scripts/workflow/wf-autopilot.sh start T-xxx                  # 自动识别批次，复用 worktree
bash scripts/workflow/wf-autopilot.sh batch-finish B-xxx           # 批次完成：批量 DoD → 批次 PR

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
10. **新功能别从零写**：不熟悉的功能别从零写, 先从 GitHub 搜开源项目对比思路再动手。

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
| 密钥扫描 | `gov-secrets-scan`（pre-push + local-ci 全车道） | `bash scripts/governance/gov-secrets-scan.sh` |
| 编码/单测 | local-ci 车道 B/C | `bash scripts/governance/gov-local-ci.sh` |
| 测试增量/分片 | `gov-affected.sh` / `scripts/testing/gov-pytest.sh` | `bash scripts/governance/gov-affected.sh --json` |
| 文档一致性 | `gov-validate.sh`（pre-push + local-ci 全车道） | `bash scripts/governance/gov-validate.sh` |
| 原型文案一致 | `gov-prototype-verify.py --strict`（task-done） | `bash scripts/governance/gov-task-done.sh T-xxx` |
| 黄金向量/Room schema | `gov-schema-guard.sh`（local-ci + task-done） | `bash scripts/governance/gov-local-ci.sh` |
| AC 完成定义 | `gov-task-done.sh` | 同上 |
| 任务归属 | 提交 trailer `Task: T-XXX` | `git log -1 --format=%B` |
| 完成证据 | `gov-task-done` 写 / 队列读 | `ls .ai/evidence` |
| 无障碍/可达性 | `gov-a11y-check.py` / `gov-reachability-check.py`（local-ci） | `bash scripts/governance/gov-local-ci.sh` |
| 钩子生效 | `gov-hook-doctor` (local-ci) | `bash scripts/governance/gov-hook-doctor.sh` |
| 绕过审计 | `gov-audit-bypass.sh` (pre-push) | `GOV_BYPASS_POLICY=block git push` |
| 工作区卫生 | `gov-workspace-doctor` (会话结束) | `gov-workspace-doctor.sh` |
| 备份残留清理 | `GOV-HYG-001`（gov-validate.sh） | `bash scripts/governance/gov-validate.sh` |
| 派生数据不手写 | 合并队列（单一写者） | `gov-board-sync.sh --dry` |
| 用户路径可走通 | `e2e_all.sh`（手动/预发） | `bash scripts/testing/e2e_all.sh` |
| 内环预算 | `dev-loop.sh --budget` | 输出自带耗时 |
| 新功能别从零写 | AI 自查 | 新功能前先 `search GitHub for similar implementations` |

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
| pre-commit | 12 段检查；源码变更时四模块增量编译（1–3 分钟），无 pytest |
| pre-push | 分支名 + 全量 validate（实测 7.3s）+ 密钥扫描 + 绕过审计，后两者并行 |
| local-ci | **合入权威**：按 `gov-ci-scope.sh` 判定车道 → 对应标签规则 + gradle + pytest |
| 队列 | 唯一写共享状态与 master 的自动化；合入前强制 `GOV_CI_FULL=1` |
| Actions | `ci.yml` 覆盖特性分支与 PR；**账单问题期间不可用**，以 local-ci 为准 |
| 分支保护 | 无（不做 Pro），靠 local-ci + PR 模板纪律 |

---

## 9. 已知缺口与退役条件

| 缺口 | 证据 | 下一步 |
|---|---|---|
| 任务板无法关闭 | 21 条 pending 全在 UI/APP 层，DoD 传参错误使其机械不可通过（见下行） | 先修 DoD，再跑 `wf-board-audit.sh` |
| 证据需手动提交 | `.ai/evidence/` 未纳入自动提交 | 由 `dev-loop`/`finish` 自动 `git add` 证据 |
| 无远程权威 | Actions 不可用、无分支保护 | 自建 bare remote + pre-receive，或恢复 required checks |
| `--no-verify` 只能发现不能阻止 | 审计默认 warn | `GOV_BYPASS_POLICY=block`，或服务端强制 |
| 多 AI 评审停更 | `docs/ai/reviews/` 最新 10-22 | 用 `review` 角色在关键节点重跑一次 |
| Notion MCP 凭据虚设 | `.opencode/mcp.json` 为 `YOUR_NOTION_API_KEY_HERE` | 补真实 token；页面公开可读，但写入需 API 鉴权 |
| 7 个 prd_ref 未入索引 | REF-notion-index.md 末待确认 | 确认 Notion 页面标题后补充 |

> **规则生命周期**：新增门禁必须同时给出执行者、负向测试、退役条件（两次误伤或 30 天零命中即进入退役评审）。没有执行者的条目只能写进"约定"，不得写进本表。

---

## 10. 本文档维护规则

- 新增门禁必须写进 §6 映射表，否则视为"约定"。
- 本文件属于共享文件（§5 共享文件清单）：单独开一支改，改完运行 `gov-sync-agent-rules.sh`。
- 本文件保持精炼：细节进 `docs/guides/GC-*.md` 与 `docs/ai/decisions/DEC-*.md`。
- **删减优于新增**：过时机制直接删，不留痕迹。