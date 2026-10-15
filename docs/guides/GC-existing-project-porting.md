# 已有项目迁移指南

> 将这套 AI 协作模式移植到已有项目中。不是复制文件，而是逐步采纳这套方法论。
>
> **核心理念**：你的项目有自己的结构、文档和约定，不需要打碎重来。本指南教你的 AI（通过 AGENTS.md）如何渐进式接入这套模式。

---

## 适用场景

已有项目遇到以下问题，说明需要这套模式：

- AI 每次接入都要重新理解项目结构，没有标准化入口
- 决策反复被推翻，没有记录追踪
- 文档散落在各处，AI 不知道该读哪些、往哪写
- 没有校验门控，AI 常创建不合规的文件
- AI 交接信息丢失，每次都要重新问

---

## 迁移方案概览

```
第 1 周：AI 入口层     → 让 AI 知道模式存在
第 2 周：文档骨架       → 搭建 docs/ 目录，不移动已有文件
第 3 周：决策习惯       → 开始记录 DEC
第 4 周：校验与钩子     → 安装最低限度的校验
第 5 周：日常运转       → 完整生命周期
```

这不是复制粘贴。每个步骤都有"最低侵入"版本和"完整采纳"版本。

---

## 第 1 步：让 AI 知道这套模式

> 目标：AI 下次启动时就能读到本指南和 AGENTS.md。
> 不需要任何文件移动。

### 方式 A：接入 AGENTS.md

```bash
# 将本模版的 AGENTS.md 内容接入到项目的 AGENTS.md 末尾
# 用 ---- 分界线隔开原内容和模版内容

cat /path/to/template/AGENTS.md >> 项目根目录/AGENTS.md
echo "" >> 项目根目录/AGENTS.md
```

AI 启动时会读取 AGENTS.md，自动知道模式存在。

### 方式 B：保留原 AGENTS.md，用本指南做入口

在项目 `AGENTS.md` 顶部加一行：

```markdown
> 协作模式参考: docs/guides/GC-existing-project-porting.md（由模版迁移而来）
```

### 方式 C：复制最小配置集

```bash
# 只复制最核心的三个文件到项目根目录
cp /path/to/template/.language .language
cp /path/to/template/AGENTS.md AGENTS.md   # 覆盖前备份原文件

# AI 过程文档目录（轻量）
mkdir -p docs/ai/decisions docs/ai/lifecycle docs/ai/guides
# 注意：不复制模版的 docs/index.md 等——你已有自己的文档
```

### 效果

AI 下次启动时会读到 AGENTS.md 中的「启动协议」和「允许文件清单」：

```
cat STATUS.md                    → 看状态
bash scripts/session/ses-context-load.sh  → 看 AI 记忆
ls -t docs/notes/ | head -3      → 看最近笔记
```

即使 STATUS.md 尚未生成、context-load.sh 尚未创建，AI 也能理解意图并主动询问或跳过。

---

## 第 2 步：搭建文档骨架

> 目标：创建模版的 docs/ 目录结构。不移动任何已有文件。

不建议直接复制模版的 `docs/` 目录（会覆盖你项目的现有文档）。而是**只创建目录结构**：

```bash
# 创建 AI 过程文档目录（这些目录原有文档不动）
mkdir -p docs/ai/decisions
mkdir -p docs/ai/lifecycle
mkdir -p docs/ai/guides
mkdir -p docs/ai/constraints
mkdir -p docs/ai/reviews

# 创建 docs/ 子目录（存在则跳过）
mkdir -p docs/notes
mkdir -p docs/archive

# 可选：架构/指南/参考目录
mkdir -p docs/architecture
mkdir -p docs/guides
mkdir -p docs/reference
mkdir -p docs/changelog
```

### 黄金标准 vs 过程文档

```
docs/ai/   ← AI 过程文档，AI 直接读写
  lifecycle/  覆盖型（每次重写）
  decisions/  追加型（永不覆盖）
  guides/     按需更新
  constraints/约束规则
  reviews/    评审报告

docs/       ← 黄金标准，晋升型
  architecture/  晋升目标
  guides/        晋升目标
  reference/     晋升目标
  changelog/     变更日志
  notes/         设计笔记
```

**关键原则**：docs/ai/ 是 AI 的工作区，docs/ 是稳定发布区。AI 只在 docs/ai/ 中直接读写，通过 promote 机制晋升到 docs/。

### 不搬文件，而是建立索引

不要将已有文档从原位置搬到 docs/。而是让 AI 知道它们存在：

在 `docs/guides/index.md` 中记录：

```markdown
## 项目原有文档索引

这些是迁移前的文档，位于项目原有位置。逐步按类型迁移到 docs/ 结构：

| 原路径 | 建议目标 | 状态 |
|--------|---------|------|
| `wiki/architecture.md` | `docs/architecture/ARCH-overview.md` |  待迁移 |
| `docs/api-spec.md` | `docs/reference/REF-api-spec.md` |  待迁移 |
```

AI 通过此索引了解全貌，每次迭代迁移一个文件，逐步归位。

---

## 第 3 步：安装校验和 Git 钩子

> 目标：安装最低限度的校验体系，不破坏现有工作流。

### 复制脚本

```bash
# 创建脚本目录
mkdir -p scripts/governance scripts/session scripts/lib

# 复制核心脚本（最小集）
cp /path/to/template/scripts/governance/gov-validate.py  scripts/governance/
cp /path/to/template/scripts/governance/gov-validate.sh   scripts/governance/
cp /path/to/template/scripts/governance/gov-next-decision.sh scripts/governance/
cp /path/to/template/scripts/session/ses-context-cache.sh  scripts/session/
cp /path/to/template/scripts/session/ses-context-load.sh   scripts/session/
cp /path/to/template/scripts/lib/lib-platform.sh           scripts/lib/
cp /path/to/template/scripts/governance/gov-update-status.sh  scripts/governance/
cp /path/to/template/scripts/governance/gov-generate-status.py scripts/governance/
cp /path/to/template/scripts/governance/gov-config.sh        scripts/governance/

# 复制 governance/modules/ 的 Python 模块
mkdir -p scripts/governance/modules
cp -r /path/to/template/scripts/governance/modules/* scripts/governance/modules/

chmod +x scripts/governance/*.sh scripts/governance/*.py scripts/session/*.sh scripts/lib/*.sh
```

### 安装 Git 钩子

```bash
mkdir -p .git/hooks

# pre-commit — 校验文档结构
cp /path/to/template/scripts/governance/gov-pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# commit-msg — 强制 Conventional Commits 格式
cp /path/to/template/scripts/governance/gov-commit-msg .git/hooks/commit-msg
chmod +x .git/hooks/commit-msg

# pre-push — 分支命名规范检查
cp /path/to/template/scripts/governance/gov-pre-push .git/hooks/pre-push
chmod +x .git/hooks/pre-push
```

> **建议**：第一周先用 commit-msg 钩子（影响最小），跑通后再加 pre-commit 和 pre-push。`--no-verify` 随时可以跳过。

### 配置源码目录

编辑 `scripts/governance/gov-config.sh`，将 `SRC_DIRS` 设为你项目的实际源码目录：

```bash
SRC_DIRS="app lib packages"    # 替换默认的 src
SRC_PREFIXES="^app/ ^lib/ ^packages/"
```

此配置影响 pre-commit 中「源码变更 → 必须含 DEC」的检测。

---

## 第 4 步：开始记录决策

> 目标：不管其他部分做没做，先养成记 DEC 的习惯。

```bash
# 创建第一条决策日志（记录迁移本身）
bash scripts/governance/gov-next-decision.sh --create
```

编辑 `docs/ai/decisions/DEC-001.md`，填写：

```markdown
# DEC-001: 接入 AI 协作协议模版

> 将 AI Project Collaboration Blueprint 的模式移植到本项目。

---

## 决策

采用渐进式迁移策略：先安装 AGENTS.md 和最小脚本集，再逐步启用
校验钩子，最终实现完整生命周期。

## 方案评分

| 方案 | 评分 | 说明 |
|------|------|------|
| 渐进迁移（采纳） | 5/5 | 不影响现有工作流，逐步采纳 |
| 全量复制 | 2/5 | 可能破坏现有构建和文档结构 |
| 不使用 | 1/5 | AI 协作效率低，决策无记录 |

## 状态

- 决策时间: 2026-07-10
- 决策者: AI + 人类
- 来源: 项目面临多 AI 协作效率瓶颈
```

---

## 第 4b 步：初始化任务看板和收件箱

任务看板和收件箱是 AI 接力协作的核心基础设施。

```bash
# 初始化任务看板
mkdir -p .ai
bash scripts/workflow/wf-task-board.sh add "接入 AI 协作协议模版"
bash scripts/workflow/wf-task-board.sh take T-001

# 设置下一步
bash scripts/workflow/wf-task-board.sh next "继续迁移，逐步启用校验钩子"

# 初始化收件箱（目录自动创建）
ls docs/ai/inbox/
```

### 任务看板使用场景

| 场景 | 命令 |
|------|------|
| 看所有任务 | `bash scripts/workflow/wf-task-board.sh list` |
| 新增任务 | `bash scripts/workflow/wf-task-board.sh add "优化 API 响应格式"` |
| 认领任务 | `bash scripts/workflow/wf-task-board.sh take T-003` |
| 标记完成 | `bash scripts/workflow/wf-task-board.sh done T-001` |
| 阻塞原因 | `bash scripts/workflow/wf-task-board.sh block T-002 "等待第三方 API 上线"` |

### 收件箱使用场景

AI Session 结束时 `ses-session-end.sh` 自动留信。下一个 AI 启动时通过 `ses-context-load.sh` 看到未读提示：

```bash
# 查收留言
bash scripts/workflow/wf-inbox.sh list

# 阅读具体消息
bash scripts/workflow/wf-inbox.sh read 20260710-083000

# 标记已读
bash scripts/workflow/wf-inbox.sh mark 20260710-083000
```

## 第 5 步：逐步启用完整生命周期

迁移不是一蹴而就的。按以下节奏推进：

| 阶段 | 内容 | 时间建议 |
|------|------|---------|
| **P0** | AGENTS.md 接入，AI 知道模式存在 | 第 1 天 |
| **P1** | DEC 记录习惯建立，`docs/ai/decisions/` 有第一条 | 第 1 周 |
| **P2** | commit-msg 钩子激活，提交格式统一 | 第 1 周 |
| **P3** | gov-validate.sh 可运行但不阻塞提交 | 第 2 周 |
| **P4** | pre-commit 激活，文档结构校验阻塞提交 | 第 3 周 |
| **P5** | lifecycle 完整 10 步运转（D→R→E→T→A→V→M→L→D→P） | 第 4 周 |
| **P6** | pre-push 激活，分支命名 + MkDocs 构建检查 | 第 4 周 |

### 各阶段命令汇总

```bash
# P1: 创建 DEC
bash scripts/governance/gov-next-decision.sh --create

# P3: 运行校验（不阻塞）
bash scripts/governance/gov-validate.sh

# P4: 更新状态
bash scripts/governance/gov-update-status.sh && cat STATUS.md

# P5: 迭代收尾
bash scripts/workflow/wf-lifecycle.sh end "本次迭代摘要"

# P5: 晋升到黄金标准
bash scripts/governance/gov-sync-standards.sh --dec DEC-00X
```

---

## 文件结构参考（安装后）

已有项目接入后，新增的文件结构：

```
项目根/
├── AGENTS.md                 ← AI 协作协议（已接入）
├── STATUS.md                 ← 项目状态仪表盘（自动生成）
├── .language                 ← 文档语言声明
│
├── scripts/                ← 核心工具链
│   ├── governance/          ← 校验 + 治理脚本
│   │   ├── gov-validate.sh       ← 校验编排器
│   │   ├── gov-validate.py       ← 校验引擎
│   │   ├── gov-next-decision.sh  ← 创建决策日志
│   │   ├── gov-update-status.sh  ← 刷新 STATUS.md
│   │   ├── gov-config.sh         ← 项目配置
│   │   ├── gov-pre-commit        ← Git 钩子
│   │   ├── gov-commit-msg        ← Git 钩子
│   │   └── gov-pre-push          ← Git 钩子
│   ├── session/             ← AI 上下文管理
│   │   ├── ses-context-cache.sh  ← AI 上下文缓存
│   │   └── ses-context-load.sh   ← 加载上下文
│   ├── workflow/            ← 协作工作流
│   │   ├── wf-task-board.sh     ← 任务看板
│   │   ├── wf-inbox.sh          ← 收件箱
│   │   └── wf-lifecycle.sh      ← 生命周期管理
│   ├── lib/                 ← 共享库
│   │   └── lib-platform.sh      ← 跨平台兼容
│   └── bootstrap/           ← 安装引导
│       └── bs-install-hooks.sh  ← Git 钩子安装
│
├── docs/ai/                  ← AI 过程文档
│   ├── lifecycle/            ← 6 个覆盖型文档
│   ├── decisions/            ← 决策日志（追加型）
│   ├── guides/               ← AI 工作指南
│   ├── constraints/          ← 行为约束
│   └── reviews/              ← 评审报告
│
├── docs/notes/               ← 设计笔记
├── docs/archive/             ← 迁移归档（如有迁移）
└── .cache/context.jsonl      ← AI 上下文
```

你的原有文件不受影响，原有的 `src/`、`tests/`、`docs/`（如有）等目录保持不动。

---

## 常见问题

### Q: 我的项目已经有 AGENTS.md 了，怎么办？

备份原文件后替换为模版的 AGENTS.md，将原内容的关键部分合入到新文件中。AGENTS.md 是 AI 的第一入口，必须使用模版的标准启动协议。

### Q: 我的项目不是 Python/Node 项目，校验能用吗？

能用。校验引擎是纯 Python，不依赖项目语言。npm 构建检查默认跳过。

### Q: 安装脚本会移动我已有的文件吗？

`setup.sh` 只复制新增文件，不覆盖已有文件（AGENTS.md 等已存在则跳过）。`migrate.sh --archive` 只复制不删除原始文件，确认无误后手动清理。

### Q: 团队中有人不用这套模式怎么办？

Git 钩子对所有提交者生效。但 `git commit --no-verify` 始终可以绕过。模式的价值在于：遵守的人越多，协作效率越高；绕过会留下审计痕迹。

### Q: 如何验证迁移进度？

```bash
# 检查已创建的文件
ls docs/ai/decisions/       # 应有 DEC-001.md
ls .git/hooks/pre-commit    # 应存在
bash scripts/governance/gov-validate.sh  # 应 0 违规
cat STATUS.md               # 查看当前任务和决策数
bash scripts/session/ses-context-cache.sh status  # AI 上下文状态
```

---

## 参考

- [决策流转规范](GC-decision-flow.md) — NOTE→DEC→lifecycle→promote→golden 完整链路
- [AI 工作流程](../ai/guides/workflow.md) — 10 步生命周期详解
- [文档标准](../ai/guides/docstandard.md) — 文档格式和质量规范
- [约束规则](../ai/constraints/index.md) — 文件命名和行为边界
