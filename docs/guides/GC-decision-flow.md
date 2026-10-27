# 决策流转规范

> 从想法到黄金标准的完整链路 — 任何 AI 首次接入必须读完本文。
>
> **真源声明**：本文件是 Notion「轻衡健康 / 00-规范 / DOC-需求到编码链路」与「DOC-文档权威与工具分工」的工程侧镜像。Notion 页面为权威，冲突时以 Notion 为准并回同步本文件。

---

## 第 0 层：真源归属（Notion vs 本仓库）

本文其余章节讲**仓库内**怎么流转。本节先讲一件更上游的事：东西该放 Notion 还是放仓库。

放错的代价不是"难看"，而是出现两份互相矛盾的最终结论 —— 链路文档明令禁止的状态。

### 两条定位

- **Notion 管"为什么、做什么、谁定的"** — 产品决策与协作
- **本仓库 `docs/` 管"怎么做、实际是什么、机器要校验的"** — 工程执行与治理

### 判据（按顺序问这五个问题）

| # | 问题 | 归属 | 理由 |
|---|------|------|------|
| 1 | 被脚本 / git 钩子 / CI 读取？ | 仓库 | 放 Notion 治理直接失效 |
| 2 | 需要非技术角色（PM/设计）编辑？ | Notion | 不该要求会写 markdown 才能改需求 |
| 3 | 必须与代码同一个 PR 变更？ | 仓库 | 否则文档与代码必然脱节 |
| 4 | 需要状态、评论、@协作、看板？ | Notion | 仓库做不了 |
| 5 | 要回溯"这个 build 依据哪份文档"？ | 仓库 | git commit 能钉住，Notion 不能 |

### 归属表

| 文档类型 | 权威方 | 说明 |
|---------|--------|------|
| 产品范围、AC 验收标准 | Notion `01-产品` | `lifecycle/01_PRD.md` 只是镜像 |
| 产品决策（品牌、范围、SLA） | Notion `DEC-*` | 决策者在 Notion |
| **工程决策**（架构、协议、库表） | 仓库 `docs/ai/decisions/DEC-*` | 要在改代码的 PR 里被引用与校验 |
| 编码与协作规范 `GC-*` | 仓库 | AI 写码时读，`gov-validate` 查 |
| 交互原型 HTML | 仓库 `design/prototypes/` | 是代码直接输入，必须版本化 |
| 任务板 `.ai/tasks.json` | 仓库 | 脚本读写，唯一 |
| 架构 / API / 协议参考 | 仓库 | 与代码同步演进 |
| 测试策略、发布流程 | 仓库（命令绑定）+ Notion（运营视图） | 各取所长，结论以 Notion 为准 |

### 承重差异

**仓库 `docs/` 是承重的，Notion 是可重建的。**

- Notion 不可用 → 从仓库能重建骨架（链路、AC 编号、任务追溯、DEC 都在）
- 仓库 `docs/` 不可用 → `gov-validate` 直接红、DEC 编号断链、AI 失去编码指引、原型追溯全断

因此仓库侧规矩必须更严（已由脚本强制），Notion 侧的规矩是：**结论必须能落到仓库**。

### 冲突处理

1. 产品口径冲突 → 以 Notion 为准，回同步仓库镜像
2. 工程口径冲突 → 以仓库 DEC 为准，回同步 Notion
3. 不确定属于哪类 → 开一个 DEC 记下归属结论，再按上表执行

**禁止**：在两边各写一份、都不标权威、等出事再争论谁对。

---

## 总览

```
  NOTE                    lifecycle/                黄金标准
  (讨论/未定型)          (覆盖型重写)             (晋升型)
                                  │
  发现问题                 写代码                  promote.sh
     │                      改文档                 sync-standards.sh
     │                        │                       │
     ▼                        ▼                       ▼
  ┌─────┐    next-decision  ┌───────┐  promote     ┌─────────┐
  │NOTE-│ ──→  DEC-XXX  ──→│lifecycle│ ────────→  │ golden  │
  │.md  │    (追加写存档)   │/*.md   │             │/*.md    │
  └─────┘                   └───────┘             └─────────┘
                                           同步
           validate.sh  ←────── sync-standards.sh
           (最终校验)
```

## 第一阶段：NOTE — 未定型的讨论记录

何时写：设计洞察、灵感、讨论记录，**尚未确定要做什么**。

```bash
# 创建设计笔记
bash scripts/governance/gov-next-note.sh create "笔记标题"
```

- 文件路径：`docs/notes/NOTE-YYYYMMDD-NNN.md`
- 命名规则：`NOTE-{日期}-{3位编号}`
- 内容可随意修改，不要求来源标注
- NOTE 不是决策，不晋升到黄金标准

## 第二阶段：DEC — 确定要做的决策

何时写：已经确定要做什么，需要记录工程决策。

```bash
# 创建决策（自动分配编号）
bash scripts/governance/gov-next-decision.sh --create
```

**执行流程：**

```
  ┌──────────────────────────────────────────┐
  │ 1. next-decision.sh --create             │
  │    创建 DEC-XXX.md 模板                   │
  │    编号自动+1，防止多 AI 冲突               │
  ├──────────────────────────────────────────┤
  │ 2. 填写 DEC 模板：                          │
  │    - 标题：短语描述                         │
  │    - 决策：为什么选这个方案                   │
  │    - 方案评分：对比表                        │
  │    - 状态：日期/决策者/来源                  │
  ├──────────────────────────────────────────┤
  │ 3. 写入 docs/ai/decisions/DEC-XXX.md      │
  │    追加写，永不覆盖 ← 铁律                   │
  └──────────────────────────────────────────┘
```

**铁律：**
- 决策日志只追加，不覆盖，不删除
- 编号必须连续（DEC-001 → DEC-002 → ...）
- 协议 / 算法 / Room schema / 同步语义 / 治理门禁 / 架构事实须对应 DEC；纯文案、注释、死 import 清理通常不需要（与 AGENTS.md、GC-multi-agent-collab、DEC-055 一致）
- DEC 内容不得留 TODO 占位符

## 第三阶段：Lifecycle — 迭代覆盖型文档

执行任务后，更新 6 个核心文档。这些是**覆盖型**文档——每次迭代重写全部内容：

| 文档 | 对应黄金标准 | 晋升目标 |
|------|-------------|---------|
| `01_PRD.md` | 产品需求 | `docs/guides/index.md` |
| `02_Architecture.md` | 系统架构 | `docs/architecture/index.md` |
| `03_TaskGraph.md` | 任务图 | — |
| `04_DevNotes.md` | 开发笔记 | — |
| `05_Test.md` | 测试 | — |
| `06_Metrics.md` | 指标 | — |

更新后 `git add` 暂存，后续 `sync-standards.sh` 会自动检测变更。

## 第四阶段：Promote — 晋升到黄金标准

将 lifecycle 文档的内容晋升到黄金标准文件。

```bash
# 手动晋升
cat 内容.md | bash scripts/workflow/wf-promote.sh docs/architecture/index.md --decision DEC-003

# 允许的晋升目标：
#   docs/architecture/index.md
#   docs/guides/index.md
#   docs/reference/index.md
#   docs/reference/REF-{slug}.md
#   docs/changelog/index.md
#   docs/notes/index.md
```

promote.sh 自动：
- 写入目标文件
- 追加 `## 来源: ai/decisions/DEC-XXX`
- 检查 git 追踪状态

## 第五阶段：Sync — 批量同步黄金标准

一个命令同步所有已变更的黄金标准：

```bash
bash scripts/governance/gov-sync-standards.sh --dec DEC-003
```

**自动检测规则（基于 git staged）：**

| staged 变更 | 同步目标 | 操作 |
|------------|---------|------|
| `02_Architecture.md` | `docs/architecture/index.md` | 覆盖写入 |
| `01_PRD.md` | `docs/guides/index.md` | 覆盖写入 |
| `DEC-xxx` | `docs/changelog/index.md` | 追加行 |
| `NOTE-*.md` | `docs/notes/index.md` | 刷新索引 |
| DEC 含"接口/API/协议"关键词 | `reference/` | 人工提醒 |

```bash
# 预览模式
bash scripts/governance/gov-sync-standards.sh --dec DEC-003 --dry-run

# 只同步指定目标
bash scripts/governance/gov-sync-standards.sh --dec DEC-003 --target architecture
```

## 第六阶段：Validate — 最终校验

晋升后必须运行全量校验：

```bash
bash scripts/governance/gov-validate.sh
# 或自动修复
bash scripts/governance/gov-validate.sh --fix
```

校验失败 = 不能提交。校验体系（gov-validate.py 编排 8 个校验模块）：
- 模块划分：status / structure / content / protocol / scripts / tests / skills / tests-gov
- 规则编号族：R0 系列 + GOV-STRUCT / GOV-CONTENT / GOV-PROTO / GOV-SCRIPT / GOV-TEST / GOV-TEST-GOV（违规即 FAIL）
  + GOV-SKILL（仅 WARN 不阻塞）
- 规则数量与清单以 `bash scripts/governance/gov-validate.sh` 实时输出为准，文档不写死数字
- GOV-TEST-005 会顺带执行 pytest，测试失败视为校验失败
- 严格执行 `set -euo pipefail`

## 完整操作示例

```bash
# 1. 发现问题 → 写 NOTE
bash scripts/governance/gov-next-note.sh create "API 响应格式统一"

# 2. 决定方案 → 创建 DEC
bash scripts/governance/gov-next-decision.sh --create
# 编辑 docs/ai/decisions/DEC-001.md

# 3. 执行任务
# ... 写代码 ...
# 更新 lifecycle/02_Architecture.md

git add docs/ai/lifecycle/02_Architecture.md
git add docs/ai/decisions/DEC-001.md

# 4. 晋升到黄金标准
bash scripts/governance/gov-sync-standards.sh --dec DEC-001

# 5. 校验
bash scripts/governance/gov-validate.sh

# 6. 提交
git add -A
git commit -m "feat(api): unify response format"
# 或使用 lifecycle 一站式收尾
bash scripts/workflow/wf-lifecycle.sh end "统一 API 响应格式"
```

## 决策树速查

```
遇到新内容
  │
  ├── 设计洞察/灵感/讨论记录（未定型）
  │   ├── 是 → NOTE-YYYYMMDD-NNN.md
  │   └── 否 ↓
  ├── 工程决策（已确定要做什么）
  │   ├── 是 → DEC-XXX.md（next-decision.sh --create）
  │   │        → sync-standards.sh --dec DEC-XXX
  │   └── 否 ↓
  ├── 生命周期阶段完成
  │   ├── 是 → 更新 6 个 lifecycle/*.md
  │   │        → promote.sh / sync-standards.sh
  │   └── 否 ↓
  └── 以上都不是 → 不可写入
```

## 注意事项

- NOTE 和 DEC 的文件名中出现英文，内容可使用中文
- lifecycle 文档是覆盖型的，每次重写不影响历史（git 追踪）
- promote.sh 不接受未知目标，只处理白名单内的黄金标准文件
- sync-standards.sh 必须指定 `--dec` 参数，不可省略
- 所有晋升操作都会在目标文件中标注来源 DEC 编号
- 本规范可以在 `docs/ai/lifecycle/06_Metrics.md` 中追踪执行率

---

## 来源: 初始创建
