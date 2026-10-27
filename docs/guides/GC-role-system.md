# DOC-角色系统与写入边界

> **版本**：v1.0（对应 `.ai/roles.json` + `wf-role.sh` + pre-commit 越界检查）
> **来源**：DEC-075（角色系统）、DEC-072（画像决定跑多深）
> **状态**：✅ 代码库已落地，📝 文档同步中

---

## 1. 角色系统设计意图

**角色只解决三件事，且只解决这三件事：**

1. **写入边界** —— 越出 allow/deny 范围，提交前机械拒绝
2. **评审独立** —— `review` 角色只读，且不能由持任务锁的会话认领（自审在机制上不可能）
3. **产物契约** —— 每个角色声明 `must_produce`，交接/合入时人工/脚本核对

> **门禁深度不在这里决定** —— 那是 `gov-profile.sh`（画像）的职责。角色决定"能不能写"，画像决定"跑多深"。

---

## 2. 角色定义单一真源：`.ai/roles.json`

```json
{
  "version": 1,
  "_comment": "AI 角色单一真源（DEC-075）。角色只约束可写范围与必须产物；门禁深度由 gov-profile.sh 决定。",
  "roles": {
    "dev": {
      "profile": "code",
      "allow": [
        "app/**",
        "domain/**",
        "application/**",
        "infrastructure/**",
        "gradle.properties",
        "gradle/**",
        "*.gradle.kts"
      ],
      "deny": [
        "scripts/**",
        ".github/**",
        "docs/ai/decisions/**",
        ".ai/tasks.json"
      ],
      "must_produce": ["task evidence"]
    },
    "test": {
      "profile": "test",
      "allow": [
        "tests/**",
        "*/src/test/**"
      ],
      "deny": [
        "app/src/main/**",
        "domain/src/main/**",
        "application/src/main/**",
        "infrastructure/src/main/**"
      ],
      "must_produce": ["failure reproduction or coverage evidence"]
    },
    "gov": {
      "profile": "gov",
      "allow": [
        "scripts/**",
        "tests/**",
        "docs/**",
        ".github/**",
        "*.md",
        ".opencode/**",
        ".gitignore",
        ".ai/roles.json",
        ".cursorrules",
        ".clinerules"
      ],
      "deny": [
        "app/src/main/**",
        "domain/src/main/**",
        "application/src/main/**",
        "infrastructure/src/main/**"
      ],
      "must_produce": ["full pytest pass"]
    },
    "docs": {
      "profile": "docs",
      "allow": [
        "docs/**",
        "design/**",
        "*.md",
        ".opencode/**"
      ],
      "deny": [
        "app/**",
        "domain/**",
        "application/**",
        "infrastructure/**",
        "scripts/**",
        "tests/**"
      ],
      "must_produce": ["index entry + reachable links"]
    },
    "review": {
      "profile": "docs",
      "read_only": true,
      "allow": [
        "docs/ai/reviews/**"
      ],
      "deny": ["**"],
      "must_produce": ["review report"],
      "conflict": "must_not_hold_task_lock"
    }
  }
}
```

---

## 3. 角色 vs 画像 vs 模式 三者关系

| 概念 | 定义位置 | 决定什么 | 典型值 |
|------|----------|----------|--------|
| **角色** | `.ai/roles.json` + `wf-role.sh` | **能写哪里** + **必须交什么** | `dev` `test` `docs` `gov` `review` |
| **画像** | `gov-profile.sh` + 认领声明 + 实际改动路径 | **跑多深**（提交环/批次环验证强度） | `docs` < `test` < `code` < `gov` < `mixed` |
| **模式** | `mode.sh` + `gov-mode.sh` + 角色层自动切 | **钩子跑不跑** / **规则跳不跳** | `dev`（关钩子） / `full`（开钩子） / `off`（逻辑层跳过） |

### 关键约束
- **角色 → 画像：只能升不能降**
  - `dev` 角色 → 画像 `code`（默认）
  - 实际改动触及 `scripts/` → 画像自动升为 `gov`（最严）
  - 显式降级需 `GOV_PROFILE_FORCE=1 GOV_PROFILE=code`
- **批次启动自动按角色切模式**：
  - 批次内有 `gov` 角色 → `mode.sh full`
  - 否则 → `mode.sh dev`

---

## 4. 角色详细规格

### 4.1 `dev` —— 功能开发主力

| 维度 | 规格 |
|------|------|
| **画像** | `code` |
| **允许写** | `app/**` `domain/**` `application/**` `infrastructure/**` + 构建文件 |
| **禁止写** | `scripts/**` `.github/**` `docs/ai/decisions/**` `.ai/tasks.json` |
| **必须产出** | `task evidence`（DoD 证据 `.ai/evidence/T-xxx.json`） |
| **典型场景** | 写功能代码、修 Bug、补单测、对齐原型文案 |
| **批次模式** | `mode.sh dev`（关钩子，跑增量） |

### 4.2 `test` —— 测试专项/质量保障

| 维度 | 规格 |
|------|------|
| **画像** | `test` |
| **允许写** | `tests/**` `*/src/test/**` |
| **禁止写** | 所有 `src/main/**` 产品代码 |
| **必须产出** | `failure reproduction or coverage evidence`（复现脚本/覆盖率报告） |
| **典型场景** | 补缺失测试、提升覆盖率、编写契约测试、故障复现 |
| **批次模式** | `mode.sh dev`（关钩子，跑受影响+全量 pytest） |

### 4.3 `docs` —— 文档/决策/原型维护

| 维度 | 规格 |
|------|------|
| **画像** | `docs` |
| **允许写** | `docs/**` `design/**` `*.md` `.opencode/**` |
| **禁止写** | 所有产品代码、测试代码、治理脚本 |
| **必须产出** | `index entry + reachable links`（索引收录 + 链接可达） |
| **典型场景** | 写 PRD/DEC/设计规范、更新原型 HTML、维护术语表 |
| **批次模式** | `mode.sh dev`（关钩子，只跑 gov-validate） |

### 4.4 `gov` —— 治理/基建/脚本维护

| 维度 | 规格 |
|------|------|
| **画像** | `gov` |
| **允许写** | `scripts/**` `tests/**` `docs/**` `.github/**` `*.md` `.opencode/**` `.gitignore` `.ai/roles.json` `.cursorrules` `.clinerules` |
| **禁止写** | 所有 `src/main/**` 产品代码 |
| **必须产出** | `full pytest pass`（全量 pytest 通过） |
| **典型场景** | 改钩子/CI/治理脚本、升级依赖、调整架构守卫、改构建逻辑 |
| **批次模式** | `mode.sh full`（开钩子，跑全量 pytest/local-ci） |

### 4.5 `review` —— 独立评审（强制第三方）

| 维度 | 规格 |
|------|------|
| **画像** | `docs`（只读） |
| **允许写** | **仅** `docs/ai/reviews/**` |
| **禁止写** | **全部**（`deny: ["**"]` + `read_only: true`） |
| **必须产出** | `review report`（署名会话，且 ≠ 认领者） |
| **冲突约束** | `must_not_hold_task_lock` —— 持有任务锁的会话**不能**认领 |
| **典型场景** | 关键节点代码评审、架构评审、安全评审、发布评审 |
| **批次模式** | 无自动测试（仅人工产出报告） |

---

## 5. 角色生命周期

### 5.1 认领（`wf-role.sh claim`）

```bash
# 认领 dev 角色，关联任务 T-081
wf-role.sh claim dev --task T-081

# 输出：
# 角色已绑定: dev
#   会话: ses-20261027-143200-a1b2
#   任务: T-081
#   允许: ["app/**","domain/**",...]
#   禁止: ["scripts/**",...]
#   必须产出: task evidence
#   有效期: 28800s (8h)
```

**拦截规则**：
- 同一会话同一时刻只能持有一个角色（再次 claim 会覆盖）
- `review` 角色要求：当前会话**不持有任何任务锁**（`wf-lock.sh --check`）
- TTL 8 小时过期自动释放（可用 `--ttl` 自定义）

### 5.2 释放（`wf-role.sh release`）

```bash
wf-role.sh release
# 角色已释放
```

### 5.3 状态查看

```bash
wf-role.sh current   # 仅输出角色名（无则空）
wf-role.sh show      # 详细：角色、画像、允许/禁止、必须产出、只读
wf-role.sh list      # 列出所有角色定义
```

---

## 6. 越界检查（机械执行点）

### 6.1 提交前自动检查（pre-commit 集成）

```bash
# 内部调用：wf-role.sh check-scope --staged
# 检查暂存区文件是否在当前角色 allow 范围内、不在 deny 范围内
```

**匹配规则**：
- `allow`：支持 `**` 通配、目录前缀、精确文件
- `deny`：优先级高于 allow
- `read_only: true`：仅允许 allow 列表内的文件，**禁止任何修改**

### 6.2 拦截示例

```bash
# 场景：dev 角色改治理脚本
$ git add scripts/governance/gov-validate.sh
$ git commit -m "fix: adjust validate rule"
❌ [越界] 角色 dev 不允许修改：
    - scripts/governance/gov-validate.sh
  处理: 换一个角色（wf-role.sh claim gov），或把改动拆到对应角色

# 场景：review 角色试图改代码
$ git add app/src/main/kotlin/.../LoginScreen.kt
$ git commit -m "refactor: minor"
❌ [越界] 角色 review 不允许修改：
    - app/src/main/kotlin/.../LoginScreen.kt
  （review 为只读角色，deny: ["**"]）
```

### 6.3 手动自检

```bash
# 检查暂存区
wf-role.sh check-scope --staged

# 检查指定文件列表
wf-role.sh check-scope --files app/src/A.kt scripts/b.sh
```

---

## 7. 评审独立性机制（`review` 角色特有）

### 7.1 认领互斥

```bash
# 同会话持有任务锁时认领 review → 直接拒绝
$ wf-role.sh claim review
拒绝: review 角色必须由不同会话承担，当前会话仍持有任务锁
      处理: 先 release 任务锁，或另开一个会话做评审
```

> **设计意图**：自审在机制上不可能。评审必须由**不同会话**（不同 AI/不同终端/不同人）承担。

### 7.2 评审报告校验

```bash
# 评审完成后，review 会话校验报告
wf-role.sh verify-review docs/ai/reviews/REV-20261027-auth.md
# 评审报告校验通过: REV-20261027-auth.md（reviewer=ses-xxx）
```

**报告必须包含**：
- `reviewer_session: <当前会话 ID>` 或 `reviewer: <当前会话 ID>`
- 评审结论、问题清单、风险等级

---

## 8. 角色在批次与队列中的体现

### 8.1 批次启动（`wf-batch.sh start`）

```bash
# 内部逻辑：
roles_in_batch = {dev: 3, test: 1}
if "gov" in roles_in_batch:
    mode.sh full   # 有 gov 角色 → 全量门禁模式
else:
    mode.sh dev    # 纯 dev/test/docs → 快速开发模式

# 测试计划合并去重：
dev    → gradlew testDebugUnitTest + gov-affected.sh --run
test   → gov-pytest.sh --all
docs   → gov-validate.sh
gov    → gov-local-ci.sh
review → (无)
```

### 8.2 合并队列（`wf-queue.sh run`）

- 队列在候选 worktree 上跑 `GOV_CI_FULL=1 gov-local-ci.sh` → **画像强制升为最高**
- 证据消费：`gov-evidence-check.sh` 读取分支里的 `.ai/evidence/T-xxx.json`，与角色无关，**缺即拒绝**

---

## 9. 常见场景操作指南

### 9.1 开发功能（`dev` 角色）

```bash
# 1. 认领角色 + 任务锁
wf-role.sh claim dev --task T-081
wf-lock.sh T-081

# 2. 开工（自动建 worktree）
wf-autopilot.sh start T-081
cd ../lightweigh-t081-auth
bash scripts/session/ses-session-begin.sh

# 3. 写代码 + 内环快速反馈
bash scripts/dev/dev-loop.sh --watch

# 4. 完成 DoD + 证据随分支提交
gov-task-done.sh T-081
git add .ai/evidence/T-081.json
git commit -m "feat(auth): deliver T-081" --trailer "Task: T-081"
```

### 9.2 改治理脚本/钩子（`gov` 角色）

```bash
# 1. 切角色（自动切 mode.sh full）
wf-role.sh claim gov --task T-090

# 2. 改 scripts/governance/xxx.sh
# ... 编辑 ...

# 3. 全量验证（gov 角色画像要求 full pytest pass）
bash scripts/governance/gov-local-ci.sh

# 4. 提交（预提交会跑暂存区 code-check + 语法检查）
git commit -m "gov(ci): optimize lane detection"
```

### 9.3 独立评审（`review` 角色）

```bash
# 前提：另开一个会话/终端，且不持有任务锁
# 会话 A：持有 T-081 锁，dev 角色写代码
# 会话 B：无锁，认领 review

# 会话 B：
wf-role.sh claim review
# 读代码、跑测试、写评审报告
cat > docs/ai/reviews/REV-20261027-auth.md <<'EOF'
reviewer_session: ses-20261027-150000-xyz
task: T-081
conclusion: PASS_WITH_COMMENTS
issues:
  - level: MINOR
    file: app/src/.../LoginScreen.kt
    desc: "硬编码颜色 Color(0xFF6200EE)，应用 QkColor.Primary"
  - level: MAJOR
    file: domain/src/.../AuthPort.kt
    desc: "缺少异常情况的契约测试"
risk: LOW
EOF

# 校验并提交
wf-role.sh verify-review docs/ai/reviews/REV-20261027-auth.md
git add docs/ai/reviews/REV-20261027-auth.md
git commit -m "review: T-081 登录页重构评审"
```

### 9.4 补测试/提覆盖率（`test` 角色）

```bash
wf-role.sh claim test --task T-095
# 只能动 tests/ 和 */src/test/
# 批次模式 dev，跑全量 pytest
bash scripts/testing/gov-pytest.sh --all
```

### 9.5 只改文档/决策（`docs` 角色）

```bash
wf-role.sh claim docs --task T-098
# 只能动 docs/ design/ *.md
# 批次模式 dev，只跑 gov-validate.sh
bash scripts/governance/gov-validate.sh
```

---

## 10. AI 规则同步（`gov-sync-agent-rules.sh`）

角色定义变更后，必须同步到各 AI 工具规则文件：

```bash
# 生成 .cursorrules / .clinerules / .opencode/rules.md
bash scripts/governance/gov-sync-agent-rules.sh

# 校验一致性（validate 集成）
bash scripts/governance/gov-sync-agent-rules.sh --check
# ❌ 不一致会报错：.cursorrules 缺少 review 角色只读约束
```

> **手改规则文件会被 `gov-validate` 判红**，只能改 `.ai/roles.json` 后重新生成。

---

## 11. 关联文档

- `DOC-治理门禁与模式开关` — 三层开关、画像、三环模型
- `DOC-批次与队列执行模型` — 批次按角色自动切 mode、测试计划
- `DOC-任务看板与并发开发` — 任务角色字段、认领锁、Autopilot
- `DOC-完成定义与证据闭环` — must_produce 与证据对应关系
- `AGENTS.md` §3/§10 — 任务画像、角色、规则→执行者映射

---

## 12. 已知缺口

| 缺口 | 影响 | 计划 |
|------|------|------|
| 生成的 AI 规则文件（`.cursorrules` 等）未包含角色边界 | 依赖人工阅读 AGENTS.md | `gov-sync-agent-rules.sh` 增加角色边界渲染 |
| `review` 角色报告模板未标准化 | 靠人工写格式 | 增加 `review-report-template.md` |
| 角色切换时未自动清理旧会话的 claims | 残留 claims 可能误判 | `wf-role.sh claim` 时自动清理旧会话 claims |
| 无角色的历史提交无法追溯画像 | 仅近期提交有 Task trailer | 历史提交补 trailer 工具 |