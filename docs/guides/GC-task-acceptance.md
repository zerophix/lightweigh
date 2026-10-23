# 任务验收标准（AC）规范

> 编码前必须有可判定的验收标准，并且钉住上层 PRD / 原型 / schema。权威任务板：`.ai/tasks.json`。决策：DEC-048、DEC-057。

---

## 1. 为何需要 AC

标题式任务（如「做报告分析 Tab」）无法让 AI 或人类判定「做完」。公开实践（Spec Kit / 规格写作）要求：结果、范围、验证命令、禁区写清。

## 2. `acceptance` 对象 schema

每个任务可选（**强烈建议** pending 即填）。`acceptance.status=ready` 与任务 `in_progress` 时，引用与 DoD **强制**（GOV-PROTO-004 / 005）：

```json
"acceptance": {
  "status": "draft | ready | blocked",
  "refs": {
    "prd": ["轻衡健康/01-产品/PRD-轻衡-HOME-001 M1 屏 H1"],
    "schema": ["infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/7.json"],
    "prototype": ["轻衡健康/03-原型补充-HTML/UI-首页"],
    "dec": ["DEC-057"],
    "pin": "PRD-轻衡-HOME-001 v1.0 @ 2026-10-22"
  },
  "given": ["前置条件…"],
  "when": ["用户/系统动作…"],
  "then": ["可观察结果…"],
  "verify_commands": ["bash …", "./gradlew …"],
  "forbidden": ["禁止改动的路径或行为…"],
  "definition_of_done": ["从上层验收原文抄来的条款…"],
  "dod_source": ["PRD-轻衡-HOME-001 AC-H1"],
  "open_questions": ["未知产品点，须人类确认后才能编码…"]
}
```

不改表、无屏时仍要写明，不能留空。理由放在条目里：`n/a: 不改 Room，只读现有测量表`、`n/a: 纯领域计算，无屏`。单独的 `n/a` 无效。

| 字段 | 必填时机 | 说明 |
|------|----------|------|
| `status` | 建议 | `draft`=占位；`ready`=可编码；`blocked`=缺人类答案或上层 pin 已变 |
| `refs.prd` | **ready / in_progress 强制** | Notion「轻衡健康」PRD 标题 + M# 或屏号。见 [GC-req-to-code-chain](GC-req-to-code-chain.md) |
| `refs.schema` | **ready / in_progress 强制** | Room 快照路径，或带理由的 `n/a:` |
| `refs.prototype` | **ready / in_progress 强制** | `UI-*` 或 `design/prototypes/` 路径，或带理由的 `n/a:` |
| `refs.dec` | 有工程决策时 | 仓库 `DEC-xxx`。产品决策写 Notion 全标题，勿与仓库编号混用 |
| `refs.pin` | **ready / in_progress 强制** | 抄录时的文档 ID + 版本或日期。验收以 pin 为准 |
| `given` / `when` / `then` | ready 时至少各 1 条 | BDD 式；勿编造未确认产品行为 |
| `verify_commands` | ready 时 ≥1 | 须真实可跑（优先模块测试 / local-ci） |
| `forbidden` | 建议 | 黄金向量、Room schema、密钥等 |
| `definition_of_done` | **ready / in_progress 强制** | 从上层验收抄来的可勾选条款；校验 GOV-PROTO-004 / 005 |
| `dod_source` | **ready / in_progress 强制** | 每条 DoD 的出处（条款号或原文短句） |
| `open_questions` | 信息不足时 | 有未答问题则 `status=blocked`，禁止开工 |

`draft` 缺 `refs` 时 `GOV-PROTO-005` 只警告，不失败。`done` / `completed` 不查。无引用的草案仍然 **不得开工**。

## 3. 工作流

1. 认领任务前：读 AC 与 pin 指向的 PRD / 原型 / schema。若 `draft`/`blocked`、缺 `refs` 或缺 DoD → 先补全或问人类，**不写业务代码**。
2. 将任务 `status` 改为 `in_progress` 前：`acceptance.status=ready`，且 `refs`（prd、schema、prototype、pin）、`definition_of_done`、`dod_source` 均非空。
3. DoD 从上层条款抄录，不按任务标题改写产品行为。上层变更打中 pin 时，本任务改为 `blocked` 并重抄，或另开任务承载增量（见链路指南第 4 节）。
4. 标记 done 前：逐条执行 `verify_commands`，对照 `then` / DoD / `dod_source`。
5. PR：勾选「AC 已覆盖」与链路引用（见 `.github/PULL_REQUEST_TEMPLATE.md`）。

## 4. 与既有完成任务的关系

看板中可能存在「标题类似、状态已 done」的旧任务（如 T-033 新手引导 vs T-045 补全）。新 AC 必须写明是**缺口补全 / 回归 / 新切片**，并指向已有 DEC 或代码路径；禁止假装从零实现已落地功能。

## 5. 示例（摘要）

见 `.ai/tasks.json` 中 T-045、T-046、T-049、T-058。

## 6. 最小任务简报（复制后填）

```json
{
  "id": "T-xxx",
  "description": "一句话：做什么，不写验收",
  "status": "pending",
  "acceptance": {
    "status": "ready",
    "refs": {
      "prd": ["轻衡健康/01-产品/<PRD 标题> <M#> 屏 <屏号>"],
      "schema": ["infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/7.json"],
      "prototype": ["轻衡健康/03-原型补充-HTML/<UI-标题>"],
      "dec": [],
      "pin": "<文档 ID> <版本或日期>"
    },
    "given": ["前置…"],
    "when": ["动作…"],
    "then": ["可观察结果…"],
    "verify_commands": ["./gradlew :app:testDebugUnitTest --tests '…'"],
    "forbidden": ["静默改 Room schema / 黄金向量"],
    "definition_of_done": ["<从上层 AC 原文抄来的一句>"],
    "dod_source": ["<PRD 文档 ID> <条款号>"],
    "open_questions": []
  }
}
```

`dec` 可为空数组。prd / schema / prototype 不可为空；不适用时写 `n/a: 理由`。

## 来源

DEC-048、DEC-057；对照 `docs/guides/GC-multi-agent-collab.md` 与 `docs/guides/GC-req-to-code-chain.md`。
