# DOC-完成定义与证据闭环

> **版本**：v1.0（对应 `gov-task-done.sh` + `gov-evidence-check.sh` + 合并队列证据消费）
> **来源**：DEC-078（完成证据双向写入+队列强制消费）、DEC-048（原型闭环验收）、DEC-064（任务锁/证据链）
> **状态**：✅ 代码库已落地，📝 文档同步中

---

## 1. 完成定义（DoD）三重校验

**命令**：`bash scripts/governance/gov-task-done.sh T-xxx`

### 1.1 校验内容（逐条执行，任一失败即 FAIL）

| 校验项 | 来源 | 通过条件 | 失败含义 |
|--------|------|----------|----------|
| **1. 产物存在性** | `task.target_files` | 每个路径在磁盘真实存在 | 代码未写/路径写错/被误删 |
| **2. 原型闭环** | `task.prototype_ref` | `gov-prototype-verify.py T-xxx --strict` 退出码 0 | 原型文案匹配率 <30% / 硬编码色/DP / 缺 Qk 组件 |
| **3. 验收命令** | `task.acceptance.verify_commands` | 逐条执行（单条超时 15min）全 0 退出码 | 单测红/构建失败/静态检查不通过 |

### 1.2 执行示例

```bash
$ bash scripts/governance/gov-task-done.sh T-081
=== 任务完成定义校验: T-081 ===

[PASS] file       app/src/main/kotlin/.../LoginScreen.kt  (存在)
[PASS] file       app/src/main/kotlin/.../AuthViewModel.kt  (存在)
[PASS] prototype  python3 scripts/governance/gov-prototype-verify.py T-081 --strict  (退出码 0, 1234ms)
[PASS] command    ./gradlew :app:testDebugUnitTest  (退出码 0, 45210ms)
[PASS] command    python3 scripts/governance/gov-prototype-verify.py T-081 --strict  (退出码 0, 1100ms)

总判定: PASS（4 项全部通过）
[evidence] 已写入 .ai/evidence/T-081.json（通过, sha=a1b2c3d）
```

### 1.3 失败示例

```bash
$ bash scripts/governance/gov-task-done.sh T-082
=== 任务完成定义校验: T-082 ===

[PASS] file       app/src/.../MeasureScreen.kt  (存在)
[FAIL] prototype  python3 scripts/governance/gov-prototype-verify.py T-082 --strict
         退出码 1
         T-082: view v-measure 原型文案匹配率 18%（3/17）低于 30%，可能未参考原型
[FAIL] command    ./gradlew :app:testDebugUnitTest
         退出码 1
         > Task :app:testDebugUnitTest FAILED
         com.xcwk.scalehub.feature.measure.MeasureFlowTest > testMeasureFlow FAILED

总判定: FAIL（2 项未通过）
[evidence] 已写入 .ai/evidence/T-082.json（未通过, sha=a1b2c3d）
```

> **关键设计**：**通过与否都写证据**（DEC-078）。失败证据的 `ok=false` 用于回答"卡在哪一步"，不留痕 = 让"部分验证"消失在历史里。

---

## 2. 证据文件格式（`.ai/evidence/T-xxx.json`）

```json
{
  "task": "T-081",
  "ok": true,
  "git_sha": "a1b2c3d4e5f6",
  "verified_at": "2026-10-27T06:32:14Z",
  "checks": [
    {
      "type": "file",
      "name": "app/src/main/kotlin/.../LoginScreen.kt",
      "status": "PASS",
      "detail": "存在",
      "duration_ms": 0
    },
    {
      "type": "prototype",
      "name": "python3 scripts/governance/gov-prototype-verify.py T-081 --strict",
      "status": "PASS",
      "detail": "退出码 0",
      "duration_ms": 1234
    },
    {
      "type": "command",
      "name": "./gradlew :app:testDebugUnitTest",
      "status": "PASS",
      "detail": "退出码 0",
      "duration_ms": 45210
    }
  ]
}
```

| 字段 | 含义 | 队列消费时校验 |
|------|------|----------------|
| `task` | 任务 ID | 必须与提交的 Task trailer 一致 |
| `ok` | 整体通过与否 | **必须为 true**（`EVIDENCE_MODE=block` 时） |
| `git_sha` | 校验时的 HEAD SHA | **必须等于分支当前 HEAD**（防旧证据作弊） |
| `verified_at` | 校验时间 | 审计用 |
| `checks[]` | 明细 | 审计/排查用 |

---

## 3. 证据必须随分支提交（机制闭环的关键）

### 3.1 为什么要进分支

> **证据不进分支 = 没做**

合并队列（`wf-queue.sh run`）在**候选 worktree**上消费证据：
```bash
# 队列内部：
# 1. 合并分支到候选树
# 2. 读取候选树里的 .ai/evidence/T-xxx.json
# 3. 校验：ok=true && git_sha == 候选树 HEAD
# 4. 缺证据 / ok=false / SHA 不匹配 → 直接拒绝合入
```

### 3.2 标准提交流程（含证据）

```bash
# 1. DoD 校验（生成证据）
bash scripts/governance/gov-task-done.sh T-081

# 2. 证据必须 git add（关键步骤！）
git add .ai/evidence/T-081.json

# 3. 提交带 Task trailer（队列据此找证据文件）
git commit -m "feat(auth): deliver T-081" --trailer "Task: T-081"

# 4. 推送 + 开 PR
git push -u origin feat/t081-auth
gh pr create --head feat/t081-auth --base master ...
```

### 3.3 自动化（`wf-autopilot.sh finish` 内部已处理）

```bash
bash scripts/workflow/wf-autopilot.sh finish T-081
# 内部自动：
# [1/4] DoD 校验 → gov-task-done.sh T-081
# [2/4] 提交 → git add 证据 + git commit --trailer "Task: T-081"
# [3/4] 推送 → git push
# [4/4] 开 PR  → gh pr create（模板自带证据清单勾选）
```

---

## 4. 队列强制消费证据（`gov-evidence-check.sh`）

### 4.1 执行时机
- **合并队列运行时**（`wf-queue.sh run` 步骤 2a）
- **手工触发**：`bash scripts/governance/gov-evidence-check.sh`

### 4.2 校验逻辑

```bash
# 环境变量控制模式：
EVIDENCE_MODE=block   # 默认：缺证据即拒绝（队列用此模式）
EVIDENCE_MODE=warn    # 仅警告：GOV_ALLOW_MISSING_EVIDENCE=1

# 校验每个带 Task: T-xxx trailer 的提交：
# 1. 存在 .ai/evidence/T-xxx.json
# 2. evidence.ok == true
# 3. evidence.git_sha == 该提交 SHA（或分支 HEAD）
```

### 4.3 拒绝示例

```bash
$ GOV_EVIDENCE_MODE=block bash scripts/governance/gov-evidence-check.sh
      [证据] 校验任务完成证据…
      ✗ T-082: 证据文件不存在 (.ai/evidence/T-082.json)
      ✗ T-083: 证据 ok=false（原型校验未通过）
      ✗ T-084: 证据 SHA 不匹配（evidence=a1b2c3d, HEAD=d4e5f6a）
      
      ❌ 完成证据不足，拒绝合入
      处理: 在对应 worktree 跑 gov-task-done.sh <T-xxx>，把 .ai/evidence/<T-xxx>.json 提交进分支
      临时放宽: GOV_ALLOW_MISSING_EVIDENCE=1（会打印，评审可见）
```

---

## 5. 原型闭环验证详解（`gov-prototype-verify.py --strict`）

### 5.1 验证链路

```
原型 HTML (design/prototypes/interactive/*.html)
  → 任务 prototype_ref (file + views[])
  → 代码 target_files[]
  → 反向检查：代码是否真的用了原型的文案/结构
```

### 5.2 匹配率算法（T-106 / DEC-071 改进）

| 维度 | 旧实现问题 | 新实现（聚合 + 逐 view） |
|------|------------|--------------------------|
| **聚合粒度** | 逐文件比对全任务 view 文案 | **任务级聚合**所有目标文件文本，再按 view 分别求率 |
| **分母** | 全任务所有 view 文案并集 | **单个 view 自己的文案** |
| **判定口径** | 任一文件承载多 view 被稀释 | **只要有一个 view 达标** 即证明参考过原型 |

### 5.3 阈值与判定

- **COPY_MATCH_THRESHOLD = 30%**：单 view 至少 30% 中文短语落在代码里
- **判定**：有 ≥1 个 view 达标 → PASS；全部 view 低于阈值 → FAIL
- **硬编码拦截**：`Color(0x...)`、`NN.dp` 必须用 `QkColor`/`QkSpacing` token

### 5.4 严格模式 vs 非严格

| 模式 | 行为 | 适用场景 |
|------|------|----------|
| `--strict` | 缺 target_files / 代码不匹配 → 退出码 1 | **DoD 校验、合入门禁** |
| 默认 | 只报告、不失败（退出码 0） | 日常检查、CI 非阻断 |

---

## 6. 验收命令规范（`acceptance.verify_commands`）

### 6.1 标准命令模板

```json
"acceptance": {
  "verify_commands": [
    "python3 scripts/governance/gov-prototype-verify.py T-081 --strict",
    "./gradlew :app:testDebugUnitTest",
    "bash scripts/governance/gov-local-ci.sh"
  ]
}
```

### 6.2 执行约束

- **工作目录**：仓库根（`gov-task-done.sh` 内部 `cwd=root`）
- **超时**：单条 900 秒（15 分钟），超时即 FAIL
- **输出截取**：失败时只保留末尾 15 行，避免刷屏
- **退出码**：非 0 即 FAIL，不看 stdout/stderr 内容

### 6.3 常用验收命令组合

| 任务类型 | 推荐 verify_commands |
|----------|----------------------|
| **原型映射**（有 prototype_ref） | `gov-prototype-verify.py T-xxx --strict` + `./gradlew :app:testDebugUnitTest` |
| **纯逻辑/算法**（domain/application） | `./gradlew :domain:test :application:test` |
| **基建/治理脚本**（scripts/） | `bash scripts/testing/gov-pytest.sh --all` + `bash scripts/governance/gov-validate.sh` |
| **文档/决策** | `bash scripts/governance/gov-validate.sh` |
| **全量验收**（批次收工/合入前） | `bash scripts/governance/gov-local-ci.sh` |

---

## 7. 证据全生命周期

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. 开发期                                                        │
│    写代码 → 内环 dev-loop.sh --watch（≤5s 快速反馈）             │
├─────────────────────────────────────────────────────────────────┤
│ 2. 任务完成声称                                                  │
│    gov-task-done.sh T-xxx                                       │
│      ├─ 产物存在性 → 原型闭环 → 验收命令                        │
│      └─ 生成 .ai/evidence/T-xxx.json（ok=true/false 都写）      │
├─────────────────────────────────────────────────────────────────┤
│ 3. 提交入库                                                      │
│    git add .ai/evidence/T-xxx.json                              │
│    git commit --trailer "Task: T-xxx"                           │
│    git push                                                     │
├─────────────────────────────────────────────────────────────────┤
│ 4. 合并队列消费                                                  │
│    wf-queue.sh run                                              │
│      ├─ 合并到候选树                                             │
│      ├─ gov-evidence-check.sh (EVIDENCE_MODE=block)             │
│      │   ├─ 证据文件存在                                         │
│      │   ├─ evidence.ok == true                                  │
│      │   └─ evidence.git_sha == 候选树 HEAD                     │
│      └─ 缺一即拒绝合入                                           │
├─────────────────────────────────────────────────────────────────┤
│ 5. 入主干后                                                      │
│    证据随分支历史永久保留，可随时审计：                          │
│    git show <commit>:.ai/evidence/T-xxx.json                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. 常见失败模式与处理

| 失败模式 | 典型原因 | 处理步骤 |
|----------|----------|----------|
| **产物缺失** | `target_files` 写错/代码未生成/被 gitignore | 1. 核对路径 2. 补代码 3. 重跑 `gov-task-done.sh` |
| **原型匹配率 <30%** | 未按原型文案实现/硬编码色/DP/缺 Qk 组件 | 1. 对照原型 HTML 逐字对齐 2. 替换硬编码 3. 重跑 |
| **单测红** | 逻辑 Bug/测试用例过期/依赖缺失 | 1. 看失败堆栈 2. 修代码/改测试 3. 重跑 verify_commands |
| **证据 SHA 不匹配** | 提交后又 amend/了 rebase/了/用旧分支跑的 DoD | 1. 重跑 `gov-task-done.sh` 生成新证据 2. git add 新证据 3. 重新提交 |
| **队列拒绝合入** | 证据未进分支/ok=false/SHA 不匹配 | 1. 在 worktree 重跑 DoD 2. git add 证据 3. git commit --amend 4. force push PR |

---

## 9. 关联文档

- `DOC-批次与队列执行模型` — 队列证据消费、批次收工 DoD
- `DOC-任务看板与并发开发` — 任务字段 `target_files`/`prototype_ref`/`acceptance`
- `DOC-治理门禁与模式开关` — 三环模型、内环预算、车道分级
- `DOC-角色系统与写入边界` — `dev` 角色 `must_produce: task evidence`
- `AGENTS.md` §7/§10 — DoD 机械校验、完成证据执行者映射

---

## 10. 已知缺口与演进

| 缺口 | 现状 | 目标 |
|------|------|------|
| 历史 83 条任务仅 ~50 条能过 DoD | 23 条无 target_files、8 条 draft AC | 分批补齐或标记 archived/blocked |
| 证据未自动 git add | 需手工/靠 `ship.sh`/`finish` 自动 | `dev-loop.sh`/`finish.sh` 统一自动 add 证据 |
| 证据可视化缺失 | 仅 JSON 文件 | Notion 证据看板（任务-证据-状态-耗时） |
| 验收命令超时 15min 对大型测试不够 | 全量 local-ci 可能 10min+ | 分层：单条命令 15min，整体 DoD 可配更长 |
| 无证据关联 PR 评审视图 | 靠人工勾选 PR 模板 | GitHub Actions 集成证据状态徽章 |