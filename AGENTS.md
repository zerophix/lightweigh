# Project Collaboration Blueprint — AI 协作协议

> 任何 AI 在此项目中工作时遵循本文件。细节见 `docs/guides/GC-*.md`。DEC-048 起本文件保持精炼。

---

## 新 AI 启动（30 秒）

```bash
cat STATUS.md                                   # 当前状态与下一步
bash scripts/session/ses-context-load.sh        # 上次会话上下文
bash scripts/workflow/wf-inbox.sh list          # 未读接力信（读即可，无时效门禁）
bash scripts/workflow/wf-claim.sh status        # 哪些任务已被其他会话占用
```

1. 有未读 inbox → 先读；它记录上一个会话的完成/未完成/坑。
2. `next_action` 未确认 → 问人类；确认后 `wf-task-board.sh confirm`。
3. 编码前读任务 `acceptance`（[GC-task-acceptance](docs/guides/GC-task-acceptance.md)）；无 ready AC 不开工。
4. 原型映射任务（T-072~T-105）：读 [GC-prototype-driven-dev](docs/guides/GC-prototype-driven-dev.md)；`gov-prototype-verify.py <T-xxx> --strict` 是闭环门禁。
5. **开工第一步是认领**：`bash scripts/workflow/wf-claim.sh claim T-xxx`（原子锁，失败说明别人在做）。

### 接力（内容优先，不计时）

- 结束用 `ses-session-end.sh` 写清：完成 / 未完成 / 决策理由 / 坑。
- 墙钟门禁（inbox 48h / session-end 24h）已于 DEC-064 退役：交接质量由内容决定，不由计时器决定。

---

---

## 核心原则：开发与质量控制解耦（DEC-064）

> 写代码时不被质量流程打断；质量在合入点一次做足。

### 1. 快慢两层

| 层 | 触发 | 内容 | 目标耗时 |
|----|------|------|----------|
| 快层 | commit / push | 分支名、禁 master、密钥扫描、文档校验（无 pytest、无全量构建） | 秒级 |
| 慢层 | 开 PR / 合入 | `gov-local-ci.sh` 全绿（合入权威，DEC-048） | 按车道决定 |

**写代码时唯一要记的**：认领任务 → 写代码 → 本地跑相关单测 → 提交推送。不要为了流程停下编码。

### 2. 风险车道（local-ci 自动判定，`GOV_LANE=auto`）

| 车道 | 触发面 | 门禁 |
|------|--------|------|
| A | `docs/ design/ .github/ scripts/ tests/ .ai/` 等非构建路径 | 不跑 gradle，只跑文档/密钥/schema/test-guard |
| B | `app/src/**`（UI 屏幕） | `:app:testDebugUnitTest` + 其余步骤 |
| C | `domain/ application/ infrastructure/ golden/ schemas/`、构建文件、未知路径 | 全量 `gradlew build` |

混合取最高；`GOV_LANE=full` 强制全量；检测失败按 C（保守）。

### 3. 共享文件单一写者

以下文件**只允许合并队列/收尾自动化写，功能分支一律不碰**：

- `.ai/tasks.json`（任务板）、`docs/ai/lifecycle/03_TaskGraph.md`（计数已改为不手写，DEC-064）
- `STATUS.md`、`tests/android-test-registry.yaml`、`docs/ai/lifecycle/06_Metrics.md`

原因：并发分支各写一份必然冲突（2026-10-24 实测 5 轮计数冲突）。功能分支只提交：**自己的代码 + 自己的测试**。

### 4. 任务级并发（不是分支级）

- 并发单元 = 一个任务（T-xxx）= 一个分支 = 一个 worktree = 一把原子锁。
- 认领：`wf-claim.sh claim T-xxx`（失败即放弃，换任务，不要重复实现）。
- 释放/过期：`wf-claim.sh release|gc`（TTL 24h）。
- 分支寿命 < 1 天；超时先 rebase 到最新 master 再继续。

### 5. 合并队列

```bash
bash scripts/governance/gov-merge.sh <PR号>
```
一条命令：拉 master → 解冲突（冲突时停下给人）→ 派生物同步 → local-ci → 合入 → 删分支/删 worktree。

### 6. 慢命令一律后台 / 并行

- 任何 > 10 秒的命令（gradle、全量本地 CI、pytest 全量）**不得阻塞主流程**：放后台，或交给子代理。
- 相互独立的任务用**子代理并行**（每个子代理一个 worktree），主线程只做评审与合并。
- 同一工作区内禁止并行跑两个校验器（会互相写文件）。

### 7. 派生数据不手写

任务计数、测试清单、状态摘要等派生信息一律从真源生成或直接引用，不写进文档——手写副本必然漂移并制造合并冲突。

### 8. 规则生命周期

新增门禁必须在 DEC 写清：**保护哪个指标、预期成本、复审条件**。两次误伤记录或 30 天零命中 → 进入退役评审。规则总数净增长不得超过产品决策增长速度。

---

## 硬规则

1. **禁止**在 `master`/`main` 直提交；分支 `feat|fix|docs|refactor|chore|test|ci/<topic>`。
2. **合入权威（无 GitHub Pro）**：`gov-merge.sh`（内部跑 `gov-local-ci.sh`）全绿 + PR 模板勾选；勿习惯性 `--no-verify`。
3. 决策：`NOTE` → `DEC` → lifecycle → `wf-promote.sh` → 黄金标准。详见 [GC-decision-flow](docs/guides/GC-decision-flow.md)。
4. 编码约定：[GC-coding-conventions](docs/guides/GC-coding-conventions.md)（模块、四件套、Room、黄金禁区）。
5. 文档语言：跟 `.language`（本仓 `zh-CN` → 中文文档）。
6. 工程反思**自愿**：[GC-engineering-reflections](docs/guides/GC-engineering-reflections.md)；REFLECT 门禁已退役（DEC-047）。
7. 提交：`<type>(<scope>): <subject>`（英文，标题 ≤72）；type 含 feat/fix/docs/test/chore/ci/refactor/build/perf/revert/style。

---

## 允许写入的文档位置

只能在下列位置新增/更新约定名的 `.md`（勿造 `old/`/`legacy/`/`vN/`）：

- `docs/guides/GC-{slug}.md`、`docs/reference/REF-{slug}.md`、`docs/archive/ARCH-{slug}.md`
- `docs/notes/NOTE-YYYYMMDD-NNN.md`、`docs/ai/decisions/DEC-XXX.md`
- `docs/ai/lifecycle/{01..06}_*.md`（覆盖型）、`docs/ai/reviews/...`、`docs/ai/inbox/...`
- 各目录 `index.md` 可更新导航；**不可**直改黄金标准正文（须 promote）

完整树与命名表见历史版本 git；以本清单 + validate 为准。

---

## 命令入口

```bash
bash scripts/governance/gov-validate.sh
bash scripts/governance/gov-custom-checks.sh
bash scripts/governance/gov-local-ci.sh
bash scripts/governance/gov-install-gitleaks.sh  # 可选：安装本地 gitleaks
bash scripts/governance/gov-next-decision.sh --create
bash scripts/workflow/wf-promote.sh <target> --decision DEC-XXX
bash scripts/bootstrap/bs-install-hooks.sh   # 安装 simplified pre-commit（DEC-047）
./gradlew :domain:test   # 更多见 GC-coding-conventions
```

规则族清单以 `gov-validate.sh` 实时输出为准（勿在文档写死条数）。生命周期 10 步见 `docs/ai/guides/workflow.md`。

---

## 本机钩子与 CI（事实）

| 层 | 行为 |
|----|------|
| pre-commit | `gov-pre-commit-simplified`：**多会话守卫**（主共享树+并发会话→强制 worktree，逃生 `ALLOW_MAIN_TREE=1`，DEC-054）、禁 master、可选会话归属、源码时 compile + custom-checks |
| pre-push | 分支名 + 秒级文档校验 + 密钥扫描；pytest 移 local-ci、删除类 push 豁免墙钟（DEC-063，可被 `--no-verify` 绕过） |
| Actions | `ci.yml`：push 到 master/main/**特性分支通配**（feat/fix/chore/…）与 **PR**（DEC-049）；文档变更仍可跳过重型构建（DEC-033） |
| Dependabot | `.github/dependabot.yml`：gradle + github-actions 周更（DEC-049） |
| 密钥扫描 | `gov-secrets-scan.sh`（gitleaks）接入 local-ci / pre-push / CI `secrets-scan` job；本地缺二进制软警告，有则硬失败 |
| STATUS 证据 | `gov-local-ci` 写入 `.cache/local-ci-last.json`，`STATUS.md` 健康检查读取（DEC-051）；可选 `gh run` |
| Schema 守卫 | 改 `golden/` 或 `infrastructure/schemas/` 须 DEC 或 `allow-schema-change`（DEC-052；CI+local-ci） |
| 测试提醒 | `src/main` 无同模块 `src/test|androidTest` 时 PR 评论警告（DEC-053；默认不红，`--strict`+`allow-no-tests`） |
| 分支保护 | **无**（用户不做 Pro）；靠 local-ci + PR 模板纪律补偿 |

---

## 平台蒸馏

| 平台 | 文件 |
|------|------|
| 通用 | `AGENTS.md`（本文件） |
| Cursor / Cline | `.cursorrules` / `.clinerules` |

任务板权威：`.ai/tasks.json`。
