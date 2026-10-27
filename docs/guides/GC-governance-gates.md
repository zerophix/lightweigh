# DOC-治理门禁与模式开关

> **版本**：v2.0（对应 `mode.sh` + `gov-mode.sh` + `wf-role.sh` + `gov-local-ci.sh` 三环模型）
> **来源**：DEC-073（版本化钩子）、DEC-064（治理总开关）、DEC-072（三环执行模型）、DEC-075（角色系统）
> **状态**：✅ 代码库已落地，📝 文档同步中

---

## 1. 三层开关架构（核心心智模型）

```
┌─────────────────────────────────────────────────────────────┐
│  物理层：mode.sh dev | full                                   │
│    • 修改 core.hooksPath 指向空目录 / 正式钩子目录            │
│    • 零开销、本机全局、所有 worktree 共享                     │
│    • 纯开发期用：彻底关掉 pre-commit/pre-push                │
├─────────────────────────────────────────────────────────────┤
│  逻辑层：gov-mode.sh off 2h | on | status                    │
│    • 数据开关：钩子仍启动、仍打印跳过日志、每次提交多几百毫秒  │
│    • 单 worktree、有期限（默认 2h、上限 8h）、到期自动恢复    │
│    • 临时放宽用：跳过记入 .ai/gov-skips/、commit 打印提示    │
├─────────────────────────────────────────────────────────────┤
│  角色层：wf-role.sh claim dev|test|docs|gov|review            │
│    • 决定"能写哪里 + 必须交什么"，不决定门禁深度             │
│    • 一个会话 = 一个角色，8h TTL 过期自动释放                 │
│    • 批次启动自动按角色切物理层：有 gov → full，否则 → dev    │
└─────────────────────────────────────────────────────────────┘
```

**关键区别**：

| 场景 | 用哪层 | 为什么 |
|------|--------|--------|
| 纯写码、不想被钩子打断 | `mode.sh dev` | 物理层零开销，钩子根本不跑 |
| 临时跳过某门禁、要留痕 | `gov-mode.sh off 2h` | 逻辑层有审计日志、自动恢复 |
| 批次开工、按角色自动切 | 角色层（内部调用 mode.sh） | 批次内角色决定 mode，无需手工切 |

---

## 2. 物理层：`mode.sh`（钩子物理开关）

### 2.1 原理
```bash
# dev 模式：core.hooksPath → .git/no-hooks（空目录）
# full 模式：core.hooksPath → scripts/hooks（版本化钩子目录）
```

### 2.2 命令
```bash
mode.sh dev       # 进入纯开发模式：提交不触发任何钩子
mode.sh full      # 恢复全量门禁：提交触发 pre-commit/pre-push
mode.sh status    # 查看当前模式
```

### 2.3 状态输出
```
模式: dev（钩子已关闭）
  进入时间: 2026-10-27T14:32:00+08:00
  core.hooksPath = .git/no-hooks
  恢复: bash scripts/dev/mode.sh full
```

### 2.4 适用场景
- ✅ 纯开发期：内环 `dev-loop.sh --watch` 快速反馈
- ✅ 批次角色为 `dev/test/docs` 时：`wf-batch.sh start` 自动切 dev
- ❌ **不适用**：PR 提交前、合入前、任何需要门禁兜底的时刻

> **警告**：`mode.sh dev` 只影响本机提交；PR/合并队列/远端 CI 仍走 `full` 模式全量门禁。

---

## 3. 逻辑层：`gov-mode.sh`（治理总开关）

### 3.1 原理
- 钩子脚本 `source gov-mode.sh` 并调用 `gov_mode_is_off`
- 返回 0（off/放行）或 1（on/继续门禁）
- 状态存在 `.ai/gov-mode.json`（worktree 级）或 `.ai/gov-mode/<session>.json`（会话级）

### 3.2 命令
```bash
gov-mode.sh off 2h      # 关闭治理 2 小时（支持 30m/90s/2h，上限 8h）
gov-mode.sh off 30m "赶截止日期，临时跳过原型校验"
gov-mode.sh on          # 立即恢复
gov-mode.sh status      # 查看状态、作用域、钩子来源、今日跳过次数
```

### 3.3 状态输出
```
governance: off，剩 85 分钟（到 2026-10-27T16:00:00+08:00，by ses-xxx，赶截止日期）
  作用域: worktree:/Users/xxx/lightweigh
  钩子来源: /abs/path/scripts/hooks
  今日跳过: 3 次
```

### 3.4 跳过审计
每次跳过记入 `.ai/gov-skips/YYYY-MM-DD.log`：
```
2026-10-27T14:32:00+08:00 gate=local-ci scope=worktree:... branch=feat/t081-auth
```

Commit/push 时打印一行提示（`GOV_QUIET=1` 静音）：
```
[gov] governance off，剩 85 分钟 → 跳过 local-ci
```

### 3.5 不豁免的门禁
> **治理总开关只关"快层"，不关"验证"**
- `gradlew build` / `gradlew test`
- `pytest` / `gov-pytest.sh`
- `gov-local-ci.sh`（批次环，唯一全量点）

---

## 4. 角色层：`wf-role.sh`（写入边界 + 评审独立 + 产物契约）

### 4.1 角色定义（单一真源：`.ai/roles.json`）

```json
{
  "dev":   { "profile": "code", "allow": ["app/**","domain/**",...], "deny": ["scripts/**",...], "must_produce": ["task evidence"] },
  "test":  { "profile": "test", "allow": ["tests/**","*/src/test/**"], "deny": ["app/src/main/**",...], "must_produce": ["failure reproduction or coverage evidence"] },
  "gov":   { "profile": "gov", "allow": ["scripts/**","tests/**","docs/**",...], "deny": ["app/src/main/**",...], "must_produce": ["full pytest pass"] },
  "docs":  { "profile": "docs", "allow": ["docs/**","design/**","*.md"], "deny": ["app/**","scripts/**",...], "must_produce": ["index entry + reachable links"] },
  "review":{ "profile": "docs", "read_only": true, "allow": ["docs/ai/reviews/**"], "deny": ["**"], "must_produce": ["review report"], "conflict": "must_not_hold_task_lock" }
}
```

### 4.2 核心命令

```bash
# 认领角色（8h TTL，可指定关联任务）
wf-role.sh claim dev --task T-081

# 查看当前角色详情
wf-role.sh show
# 角色: dev
#   画像: code
#   允许: app/**, domain/**, application/**, infrastructure/**, gradle.properties, gradle/**, *.gradle.kts
#   禁止: scripts/**, .github/**, docs/ai/decisions/**, .ai/tasks.json
#   必须产出: task evidence
#   只读: false

# 提交前越界检查（pre-commit 自动调用）
wf-role.sh check-scope --staged

# 列出所有角色
wf-role.sh list

# 释放角色
wf-role.sh release

# 评审报告校验（review 角色专用）
wf-role.sh verify-review docs/ai/reviews/REV-001.md
```

### 4.3 越界拦截示例

```bash
# dev 角色改了治理脚本
git add scripts/governance/gov-validate.sh
git commit -m "fix: adjust validate"
# ❌ 预提交拒绝：
# [越界] 角色 dev 不允许修改：
#     - scripts/governance/gov-validate.sh
#   处理: 换一个角色（wf-role.sh claim gov），或把改动拆到对应角色
```

### 4.4 评审独立性保证

```bash
# 同会话持有任务锁时认领 review → 直接拒绝
wf-role.sh claim review
# 拒绝: review 角色必须由不同会话承担，当前会话仍持有任务锁
#       处理: 先 release 任务锁，或另开一个会话做评审

# 评审报告必须署名当前会话
wf-role.sh verify-review docs/ai/reviews/REV-001.md
# 拒绝: 报告未署名当前会话（需包含 reviewer_session: ses-xxx）
```

---

## 5. 三环执行模型（快慢彻底分离）

| 环 | 预算 | 命令 | 跑什么 | **不跑什么** | 触发时机 |
|----|------|------|--------|--------------|----------|
| **内环** | ≤ 5s | `dev-loop.sh [--watch] [--budget 5]` | 增量编译 + 同名 Kotlin 测试 + 受影响 pytest | git、证据、全量扫描 | 编码时、文件保存时 |
| **交付环** | ≤ 5s | `ship.sh -m "..." <path>` | 暂存 + 提交 + 推送 + PR；全量验证放后台 | 全量测试、证据生成 | 任务完成、要开 PR 时 |
| **提交环** | ≤ 3s | `git commit` | 分支/范围/角色/`bash -n`/暂存区 code-check/增量编译 | pytest、可达性、原型、全量 | 每次提交 |
| **批次环** | ≤ 10min | `gov-local-ci.sh` | 全量测试（分片）+ 静态检查（内容哈希缓存）+ gradle | — | 合入队列、批次收工、显式调用 |

### 5.1 内环预算机制（DEC-072）

```bash
# 开发时：单次跑（≤5s）
bash scripts/dev/dev-loop.sh

# 监视模式：文件变化自动重跑（2s 轮询）
bash scripts/dev/dev-loop.sh --watch

# 超预算显式交棒（不报错、不偷跑）
# [预算] 已用 7s ≥ 5s，跳过受影响 pytest
#        剩余验证交给批次环: bash scripts/governance/gov-local-ci.sh
```

> **铁律**：内环超预算**不偷偷跑 30 秒**，显式把责任移交给批次环。

### 5.2 交付环（`ship.sh`，新增）

```bash
# 一条命令：暂存 → 提交 → 推送 → 开 PR
# 全量验证放后台（wf-run.sh），不阻塞当前会话
bash scripts/dev/ship.sh -m "feat(auth): deliver T-081" .
```

### 5.3 画像决定"跑多深"（只能升不能降）

| 画像 | 允许改动 | 提交环 | 批次环 |
|------|----------|--------|--------|
| `docs` | `docs/` `design/` `*.md` | 范围+语法 | 车道 A（不构建不测试） |
| `test` | `tests/` `*/src/test/` | 范围+语法 | 全量 pytest |
| `code` | `app/` `domain/` `application/` `infrastructure/` + 构建文件 | 增量编译+暂存区检查 | gradle + 受影响 pytest |
| `gov` | `scripts/` `tests/` `docs/` `.github/` + 根规则 | 暂存区检查+语法 | 全量 pytest + 静态 |
| `mixed` | 任意（兜底，最严） | 并集 | 全量 |

> **降级只能显式**：`GOV_PROFILE_FORCE=1 GOV_PROFILE=<p>`

---

## 6. 车道分级 CI（`gov-local-ci.sh` + `gov-ci-scope.sh`）

### 6.1 车道判定（按变更路径自动判定）

| 车道 | 触发路径 | 执行内容 | 典型耗时 |
|------|----------|----------|----------|
| **A** | `docs/` `design/` `.github/` `scripts/` `tests/` `*.md` | gov-validate + custom-checks + secrets + schema + test-guard + a11y + reachability + hook-doctor | **< 30s**（跳过 gradle） |
| **B** | `app/src/**` | `:app:testDebugUnitTest` + 上述非 gradle 步骤 | **1-3 min** |
| **C** | `domain/` `application/` `infrastructure/` `golden/` `schemas/` 构建文件 未知路径 | `./gradlew test`（全量）+ 上述步骤 | **6-10 min** |

> **混合取最高**：C > B > A；检测失败按 C 处理。

### 6.2 强制车道
```bash
GOV_LANE=A|B|C|full bash scripts/governance/gov-local-ci.sh
# 或
bash scripts/governance/gov-local-ci.sh --strict  # 等价 full
```

### 6.3 构建锁并发控制（DEC-069）
多 worktree 并发时串行化 gradle，避免 daemon 争抢：
```bash
# 内部使用 .cache/build.lock.d 目录锁
# 超过 40 分钟视为僵尸锁自动清理
# 超时 30 分钟放弃等待
```

---

## 7. 门禁执行者映射表（AGENTS.md §10 核心）

| 规则 | 执行者（机械） | 自查命令 |
|------|----------------|----------|
| 禁止直提 master | pre-commit | `git branch --show-current` |
| 分支命名 | pre-push | 同上 |
| 任务认领互斥 | `wf-lock.sh` + pre-commit | `wf-lock.sh --check T-xxx` |
| **角色写入边界** | `wf-role.sh` + pre-commit | `wf-role.sh show` |
| **任务画像** | `gov-profile.sh` + pre-commit + local-ci | `gov-profile.sh explain` |
| **AI 规则一致** | `gov-sync-agent-rules.sh --check` | 同左 |
| 提交归属声明 | `ses-claim-staged.sh` + pre-commit | 提交成功即通过 |
| 密钥扫描 | pre-push + local-ci + CI | `gov-secrets-scan.sh` |
| 编码/单测 | local-ci 车道 B/C | `gov-local-ci.sh` |
| **测试增量与分片** | `gov-affected.sh` / `gov-pytest.sh` | `gov-affected.sh --json` |
| 文档一致性 | `gov-validate` | `gov-validate.sh` |
| 原型文案一致 | `gov-prototype-verify --strict` | `gov-task-done.sh T-xxx` |
| 黄金向量/Room schema | `gov-schema-guard` | `gov-local-ci.sh` |
| AC 完成定义 | `gov-task-done.sh` | 同上 |
| 任务归属 | 提交 trailer `Task: T-XXX` | `git log -1 --format=%B` |
| **完成证据** | `gov-task-done` 写 / `gov-evidence-check` + 队列读，缺即拒绝 | `ls .ai/evidence` |
| 无障碍/预览/屏可达 | `gov-a11y-check` / `gov-preview-check` / `gov-reachability-check` | 对应脚本 |
| 钩子生效 | `gov-hook-doctor` | `bash scripts/governance/gov-hook-doctor.sh` |
| **绕过审计** | `gov-audit-bypass.sh`（pre-push） | `GOV_BYPASS_POLICY=block git push` |
| 工作区卫生 | `gov-workspace-doctor` | `gov-workspace-doctor.sh` |
| 派生数据不手写 | 合并队列（单一写者） | `gov-board-sync.sh --dry` |
| 用户路径可走通 | **手动/预发**：`e2e_all.sh` | `bash scripts/testing/e2e_all.sh` |
| **内环预算** | `dev-loop.sh --budget` | 输出自带耗时 |

---

## 8. 绕过审计（`--no-verify` 发现与阻断）

```bash
# pre-push 默认：发现即警告（审计日志）
# 环境变量阻断模式：
GOV_BYPASS_POLICY=block git push
# → 发现 --no-verify 直接拒绝推送
```

---

## 9. 常用操作速查

```bash
# 物理开关（纯开发/交付前）
mode.sh dev          # 写码期，零钩子开销
mode.sh full         # 交付/合入前，恢复全量门禁
mode.sh status       # 确认当前模式

# 逻辑开关（临时放宽、留痕）
gov-mode.sh off 2h "赶进度，临时跳过原型校验"
gov-mode.sh on       # 立即恢复
gov-mode.sh status   # 看剩余时间、跳过次数

# 角色（批次自动切，手工仅在跨角色改动时用）
wf-role.sh claim gov --task T-081   # 改治理脚本时
wf-role.sh claim review             # 评审时（必须另开会话）
wf-role.sh check-scope --staged     # 提交前自检

# 三环验证
dev-loop.sh --watch                 # 内环：编码时持续跑
ship.sh -m "feat: deliver T-081" .  # 交付环：提交+推送+PR
git commit                          # 提交环：自动跑
gov-local-ci.sh                     # 批次环：合入前/批次收工全量

# 车道查看
bash scripts/governance/gov-ci-scope.sh --print-lane
# 输出：lane=B forced=false reason=... path=...
```

---

## 10. 关联文档

- `DOC-批次与队列执行模型` — 批次按角色自动切 mode、队列唯一全量点
- `DOC-任务看板与并发开发` — 任务角色字段、认领锁、Autopilot
- `DOC-角色系统与写入边界` — 角色定义详细、must_produce、越界拦截
- `DOC-完成定义与证据闭环` — DoD 三重校验、证据随分支提交
- `AGENTS.md` §0.1/§2/§3/§10/§13 — 总开关、三环模型、角色、钩子与 CI

---

## 11. 已知缺口

| 缺口 | 现状 | 目标 |
|------|------|------|
| `mode.sh dev` 物理关钩子可能推脏代码 | 仅本地有效，队列仍全量 | 远端 pre-receive 强制 full |
| `gov-mode.sh` 逻辑层每次提交仍有几百毫秒开销 | 钩子仍启动、打印跳过日志 | 接受：权衡留痕 vs 性能 |
| 角色边界对生成文件（`.cursorrules` 等）不生效 | `gov-sync-agent-rules.sh` 生成后不查 | 生成后自动校验 |
| 车道判定对新增模块可能误判 C | 保守策略 | 增加模块白名单配置 |