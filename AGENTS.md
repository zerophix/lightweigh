# Project Collaboration Blueprint — AI 协作协议

> 任何 AI 在此项目中工作时，自动遵循以下规则。
> 这是一个通用模板，按项目需求修改 轻刻健康 内容。

---

## 新 AI 启动协议（必须执行）

每次新 session 启动时，按以下顺序执行：

```bash
# 第 1 步：看项目状态
cat STATUS.md                    # 项目当前状态（规则数、DEC 数、健康度）
bash scripts/session/ses-context-load.sh  # AI 上下文记忆 + 任务看板 + 收件箱

# 第 2 步：查收接力信息
bash scripts/workflow/wf-inbox.sh list     # 查收上一个 AI 的留言
bash scripts/workflow/wf-inbox.sh read <id>  # 逐条阅读（id 从 list 获取）
bash scripts/workflow/wf-task-board.sh list  # 看任务看板状态
ls -t docs/notes/ | head -3      # 最近 3 篇设计笔记
ls -t docs/ai/decisions/ | head -3  # 最近 3 条决策

# 第 3 步：向人类确认接力方向
# session-begin.sh 已自动提示下一步。AI 必须问人类：
# "上一个 AI 计划做 [X]，理由是 [Y]。要继续吗？"
# 人类确认后执行: bash scripts/workflow/wf-task-board.sh confirm

# 第 4 步：看 AGENTS.md（本文件）→ 项目规则
# 第 5 步：开始工作
```

### 多 AI 并发

详见 [`docs/guides/GC-multi-agent-collab.md`](docs/guides/GC-multi-agent-collab.md)。开始会话：`ses-session-begin.sh`；暂存后：`ses-claim-staged.sh`；合入前：`bash scripts/governance/gov-local-ci.sh`。

### 接力铁律

1. **必须查收件箱** — 有未读消息时不读直接干活 = 违规
2. **必须问人类确认** — next_action 未确认时直接执行 = 违规
3. **结束必须交接** — `bash scripts/session/ses-session-end.sh` 跑完才结束 session
4. **交接必须 4 项** — 完成 + 未完成 + 决策理由 + 风险，缺一不可

### 启动时需要回答的 5 个问题

```
1. 这是什么项目？          → STATUS.md + AGENTS.md
2. 上一个 AI 留了什么？    → inbox.sh list + task-board.sh list
3. 人类确认方向了吗？      → task-board.sh list 看 next_action 确认状态
4. 决策如何流转？         → docs/guides/GC-decision-flow.md（必须读）
5. 我不该重复什么？        → context-load.sh 输出的 learning 和 known-issue
```

### 30 秒速览

1. 文档分两层: docs/（黄金标准）+ docs/ai/（AI 过程文档）
2. 每轮迭代 10 步: D→R→E→T→A→V→M→L→D→P（见下文）
3. 多 AI 评审: `/评审` 或 `/review` 触发，报告写入 reviews/{date}_{topic}/{agent}.md
4. 接力协作: session-begin 强制读信 → 问人类确认 → 工作 → session-end 强制交接
5. Git hook 已安装: pre-commit + commit-msg + pre-push
6. 提交格式: <type>(<scope>): <subject>（≤72字符，纯英文）
7. 状态看板: `cat STATUS.md`（自动生成，不手动编辑）

---

## 允许的文件清单

AI 只能写入以下文件，**不得创建新的 `.md` 文档**到任何其他位置：

```
docs/
├── index.md                    ← 首页（可更新导航链接）
├── architecture/index.md       ← 晋升：架构变更
├── guides/index.md             ← 索引（可更新，收录 GC-*.md）
├── guides/GC-{slug}.md         ← 规范/指南正文（新增）
├── reference/index.md          ← 索引（可更新，收录 REF-*.md）
├── reference/REF-{slug}.md     ← 接口/协议/参考正文（新增）
├── archive/ARCH-{slug}.md      ← 旧文档归档（迁移过渡用）
├── changelog/index.md          ← 晋升：决策已生效
├── notes/index.md              ← 索引（可更新，收录 NOTE-*.md）
├── notes/NOTE-YYYYMMDD-NNN.md  ← 设计笔记正文（新增）
├── standard.md                 ← 黄金标准首页（可更新链接）
└── ai/
    ├── lifecycle/
    │   ├── 01_PRD.md           ← 覆盖型，命名固定
    │   ├── 02_Architecture.md  ← 覆盖型，命名固定
    │   ├── 03_TaskGraph.md     ← 覆盖型，命名固定
    │   ├── 04_DevNotes.md      ← 覆盖型，命名固定
    │   ├── 05_Test.md          ← 覆盖型，命名固定
    │   └── 06_Metrics.md       ← 覆盖型，命名固定
    ├── decisions/
    │   └── DEC-XXX.md          ← 追加型，XXX 为 3 位数字
    ├── guides/index.md
    ├── guides/workflow.md      ← 只更新不重命名
    ├── guides/docstandard.md   ← 只更新不重命名
    ├── guides/consensus.md     ← 只更新不重命名
    ├── constraints/index.md
    ├── reviews/
    │   ├── index.md
    │   └── {date}_{topic}/{agent}.md
    └── inbox/
        └── {YYYYMMDD-HHMMSS}.md    ← AI 接力交接信（追加型）
```

### 命名规则

| 文件类型 | 命名模式 | 示例 |
|---------|---------|------|
| 生命周期文档 | `{编号}_{名称}.md` | `01_PRD.md` |
| 决策日志 | `DEC-{3位数字}.md` | `DEC-001.md` |
| 指南正文 | `GC-{slug}.md` | `GC-coding-conventions.md` |
| 参考文档 | `REF-{slug}.md` | `REF-api-spec.md` |
| 归档文档 | `ARCH-{slug}.md` | `ARCH-v0.9-docs.md` |
| 设计笔记 | `NOTE-YYYYMMDD-NNN.md` | `NOTE-20260706-001.md` |

### 禁止行为

- **不创建** `old/`、`legacy/`、`vN/` 等版本目录
- **不重命名**现有文件
- **不添加**未在此清单中的 `.md` 文件
- **不可直接编辑** `docs/` 根目录下的黄金标准文件（必须经过生命周期 → 晋升）
- 文件名必须英文 ASCII
- **文档语言：中文** — 所有文档必须使用中文撰写，禁止中英文混写（见 `.language` 文件）
- AI 撰写文档时，如果 `.language` 声明 `zh-CN`，则全文使用中文；如果声明 `en`，则全文使用英文

---

## 文档架构

```
docs/ ← 黄金标准（晋升型，可追溯）
├── architecture/   ← index.md（单一文件，必须）
├── guides/         ← index.md + GC-*.md 多文件
├── reference/      ← index.md（单一文件，必须）
├── changelog/      ← index.md（单一文件，必须）
├── notes/          ← index.md + NOTE-*.md 多文件
├── standard.md
└── index.md

docs/ai/ ← AI 过程文档
├── lifecycle/   （迭代覆盖型，每次重写）
├── decisions/   （追加型，永不覆盖）
└── guides/      （半稳定型，按需更新）
```

---

## 生命周期 10 步（每次迭代）

每次迭代按照 D→R→E→T→A→V→M→L→D→P 顺序执行：

```
D: 读取全部当前状态
   读 STATUS.md → 知道项目状态
   读 context-load.sh → 知道 AI 记忆
   读最近 notes + decisions → 知道上下文

R: 多 AI 评审（可选）
   各 AI 分别写评估到 reviews/{date}_{topic}/{agent}.md
   收敛后写 conclusion.md

E: 决策引擎
   基于评审结果，生成方案，评分选择

T: 生成任务图
   分解为可执行的任务列表

A: 执行任务
   写代码、改文档、创建 DEC

V: 验证器
   运行 validate.sh 检查文档结构
   运行测试/构建验证代码

M: 更新指标
   运行 update-status.sh
   运行 update-metrics.sh

L: 记录决策日志 + 接力交接
   DEC-XXX.md（next-decision.sh --create）
   bash scripts/session/ses-session-end.sh（接力清单 + 收件箱 + 任务板）

D: 更新 6 核心文档
   覆盖写入 lifecycle/ 的 6 个文件

P: 晋升到黄金标准
   promote.sh 联动更新 docs/ 下的文件
   sync-standards.sh 批量同步
```

---

## 文档写入决策树

```
遇到新内容
  │
  ├── 工程决策（已确定要做什么）
  │   ├── 是 → DEC-XXX.md（next-decision.sh --create）
  │   └── 否 ↓
  ├── 设计洞察/灵感/讨论记录（未定型）
  │   ├── 是 → NOTE-YYYYMMDD-NNN.md
  │   └── 否 ↓
  ├── 规范/指南/教程（稳定内容）
  │   ├── 是 → docs/guides/GC-{slug}.md
  │   └── 否 ↓
  ├── 架构变更
  │   ├── 是 → 更新 02_Architecture.md → promote.sh
  │   └── 否 ↓
  └── PRD/需求
      ├── 是 → 更新 01_PRD.md → promote.sh
      └── 否 → 不可写入
```

---

## 写入后立即校验

```bash
bash scripts/governance/gov-validate.sh
```

有 FAIL → 必须修复后才能继续。每次单文件写入后立即校验。

---

## Git 提交规范

```
<type>(<scope>): <subject>

<body>

<footer>
```

| type | 何时使用 |
|------|---------|
| feat | 新功能 |
| fix | bug 修复 |
| docs | 文档变更 |
| style | 代码格式化 |
| build | 构建/依赖变更 |
| refactor | 代码重构 |
| revert | 回退 |
| test | 测试 |
| perf | 性能优化 |
| ci | CI 配置 |
| chore | 杂项 |

### 规则

- 标题 ≤72 字符，纯英文
- scope 为小写英文/数字/连字符
- 正文每行 ≤72 字符
- 分支名: `<type>/<description>`（feat/xxx, fix/xxx 等）

---

## 多平台兼容

本项目规则已分发到各 AI 平台的配置文件中：

| 平台 | 配置文件 | 内容 |
|------|---------|------|
| 通用（OpenCode/Codex/Cline 等） | `AGENTS.md`（本文件） | 完整协作协议 |
| Cline | `.clinerules` | 核心流程蒸馏 |
| OpenCode | `.opencode/rules.md` | 核心流程蒸馏 |
| Cursor | `.cursorrules` | 核心流程蒸馏 |

各平台配置均已包含：启动流程、决策流转、文件规范、校验命令；工程反思为自愿追加（见 GC-engineering-reflections.md）。
`setup.sh` 安装时自动复制这些文件到目标项目。

> 自 DEC-003 起，协议文件统一为 `AGENTS.md`（跨平台标准名）。
> Claude Code 旧版本若不识别 `AGENTS.md`，可在项目根执行 `ln -s AGENTS.md <旧文件名>` 建立软链兼容；
> `.claude/` 目录（hooks、skills）是 Claude Code 的工具配置，不随协议文件更名。

## 命令汇总

```bash
# ── 核心校验 ──
bash scripts/governance/gov-validate.sh                  # 全量校验（规则族见文末速查表）
bash scripts/governance/gov-validate.sh --fix            # 自动修复 + 校验
bash scripts/governance/gov-strip-emoji.py               # emoji 清除（被 validate 调用）
bash scripts/governance/gov-strip-emoji.py --dry-run     # 仅检测不修改

# ── 状态与仪表盘 ──
bash scripts/governance/gov-update-status.sh             # 生成 STATUS.md
bash scripts/governance/gov-update-metrics.sh            # 更新指标

# ── 模板升级（分叉/下游使用, DEC-010）──
bash scripts/governance/gov-upgrade.sh --check <模板路径>  # 体检与上游拥有区差异
bash scripts/governance/gov-upgrade.sh --apply <模板路径>  # 备份后同步拥有区

# ── 文档晋升 ──
bash scripts/workflow/wf-promote.sh <target> --decision <DEC>   # 晋升写入黄金标准
bash scripts/governance/gov-sync-standards.sh --dec <DEC>          # 批量同步黄金标准
bash scripts/governance/gov-sync-mkdocs.sh                         # 同步 mkdocs.yml 导航
bash scripts/governance/gov-lifecycle-audit.sh                     # 源码变更必须含 DEC + DevNotes（本地/CI 共用）

# ── 决策与笔记 ──
bash scripts/governance/gov-next-decision.sh --create    # 创建决策模板（原子编号）
bash scripts/governance/gov-next-note.sh create "title"  # 创建设计笔记
bash scripts/governance/gov-generate-index.sh            # 刷新决策日志索引
bash scripts/governance/gov-validate-decisions.sh        # DEC 内容真实性校验

# ── 上下文与记忆 ──
bash scripts/session/ses-context-cache.sh             # 上下文缓存管理
bash scripts/session/ses-context-load.sh              # 加载 AI 上下文

# ── AI 接力协作 ──
bash scripts/workflow/wf-task-board.sh list           # 看任务看板
bash scripts/workflow/wf-task-board.sh add "..."      # 新增任务
bash scripts/workflow/wf-task-board.sh done T-001     # 标记完成
bash scripts/workflow/wf-task-board.sh next "..."     # 设置下一步
bash scripts/workflow/wf-inbox.sh list                # 查收留言
bash scripts/workflow/wf-inbox.sh read <id>           # 读取留言
bash scripts/workflow/wf-inbox.sh send "主题" "内容"   # 留信
bash scripts/session/ses-session-end.sh               # Session 收尾 + 接力交接

# ── 项目迁移 ──
bash scripts/migration/mig-migrate.sh --scan            # 扫描散落文件
bash scripts/migration/mig-migrate.sh --archive         # 归档到 docs/archive/
bash scripts/migration/mig-migrate.sh --apply           # 自动分类迁移

# ── Git 钩子 ──
bash scripts/bootstrap/bs-install-hooks.sh             # 安装 3 个 git hook
scripts/governance/gov-commit-msg                        # 提交信息格式（被 git 调用）
scripts/governance/gov-pre-commit                        # 提交前自动修复（被 git 调用）
scripts/governance/gov-pre-push                          # 推送前分支校验（被 git 调用）

# ── 辅助 ──
bash scripts/session/ses-context-auto-save.sh         # 自动保存上下文
bash scripts/governance/gov-custom-checks.sh             # 项目自定义检查
```
bash scripts/session/ses-context-load.sh     # 加载上下文
bash scripts/migration/mig-migrate.sh --scan   # 扫描散落文件并规划迁移

## MkDocs 文档站点

> **注意：环境前提**：本项目 mkdocs 服务从 `conda activate mkdocs` 环境启动。
> 继承本模版的项目需安装同一环境，否则 `mkdocs` 命令不可用。

**首次安装：**
```bash
conda env create -f environment.yml    # 创建 mkdocs 环境（只需一次）
```

**日常使用（每次新终端）：**
```bash
conda activate mkdocs                   # 激活 conda 环境
mkdocs serve                            # 本地预览 → http://localhost:8000
```

**备用方案（pip 直接安装）：**
```bash
pip install -r requirements.txt
mkdocs serve
```

**构建发布：**
```bash
mkdocs build                            # 输出到 site/ 目录
```

pre-push 钩子在主干分支推送前自动运行 `mkdocs build`，构建失败则阻止推送。
CI 中 `docs-build` Job 构建站点，`deploy` Job 发布到 GitHub Pages。

## CI/CD

| Job | 触发条件（以 `.github/workflows/ci.yml` 为准） | 作用 |
|-----|---------|------|
| `build-and-guard` | `push` 到 `main`/`master`，以及 **pull_request** | `./gradlew build` + `assembleAndroidTest`（纯文档变更可跳过重型步骤） |
| `docs-validate` | 同上 | `gov-validate.sh` 全量校验 |
| `lifecycle-audit` | 同上（push 与 PR 均有） | 源码变更须含 DEC + DevNotes |

勘误（2026-10-22 / DEC-045）：
- 特性分支**裸 push 不触发** CI，需开 PR 或改 workflow；无分支保护时 CI 不阻断合入。
- **pre-push** 实际只做：分支命名 + 全量 `gov-validate.sh`（**不含** Gradle、**不含**单独的 DEC 审计行；lifecycle-audit 在 CI）。
- 本机 **pre-commit** = `gov-pre-commit-simplified`（编译 + **gov-custom-checks**），与模板 `gov-pre-commit` 不同；DEC-046。
- 合并前请跑：`bash scripts/governance/gov-local-ci.sh`（build + validate + custom-checks）。
- **强制 README 反思门禁已拆除**；工程反思见 `docs/guides/GC-engineering-reflections.md`（自愿）。`docs/ai/constraints` 中 REFLECT-001~003 正文待另 DEC 退役。

pre-push 可被 `--no-verify` 绕过；CI 在开启分支保护并设为 required 后才是硬门禁。
`lifecycle-audit` 实现：`bash scripts/governance/gov-lifecycle-audit.sh`。

---

## 校验规则速查

> 规则数量与清单以 `bash scripts/governance/gov-validate.sh` 实时输出为准（单一事实源，勿在文档中写死数字）。
> 引擎: `gov-validate.py` 编排 9 个校验模块（`scripts/governance/modules/`）。

| 规则族 | 检查内容 | 级别 |
|--------|---------|------|
| R0、R0b~R0f | git hooks 安装、语言配置、点目录配置、依赖目录位置、根目录文件、隐藏目录白名单 | FAIL |
| GOV-STRUCT-001~007 | 非 ASCII 文件名、遗弃路径、游离 .md、结构白名单、命名规范、来源标注、内容深度 | FAIL |
| GOV-STRUCT-019~020 | Emoji 字符检测、语言一致性（按 .language 声明） | FAIL |
| GOV-README-001~005 | README 结构规范：H1 唯一开头（硬）、快速开始顺序（缺失为 WARN）、相关文档入口（WARN） | FAIL |
| GOV-CONTENT-001~005 | 生命周期绑定、指标时效、changelog 非空、决策编号连续、会审报告规范 | FAIL |
| GOV-CONTENT-008~011 | 项目记忆格式与时效、README 占位符与 Emoji | FAIL |
| GOV-PROTO-001~003 | 收件箱 48h 未读、session-end 24h 时效、僵尸任务 | FAIL |
| GOV-SCRIPT-001~009 | 脚本 registry 存在、registry 完整性（GOV-SCRIPT-004 反向校验, DEC-023）、目录前缀、命名坏味道、根目录裸脚本 | FAIL |
| GOV-TEST-001~005 | 治理测试命名/结构/Emoji + pytest 执行（测试失败即校验失败） | FAIL |
| GOV-TEST-GOV-001~010 | 测试 registry 完整性、前缀、反向校验、空目录 | FAIL |
| GOV-ATEST-001~005 | Android 单元测试治理：非 ASCII/命名/版本/Emoji/注册表完整性（DEC-023） | FAIL |
| GOV-SKILL-001~003 | 技能约束（.claude/skills 相关，仅提示不阻塞） | WARN |

注意：规则编号已从旧版 R1~R20 重命名为 GOV-* 族（R0 系列保留）。
