# 任务验收标准（AC）规范

> 编码前必须有可判定的验收标准。权威任务板：`.ai/tasks.json`。决策：DEC-048。

---

## 1. 为何需要 AC

标题式任务（如「做报告分析 Tab」）无法让 AI 或人类判定「做完」。公开实践（Spec Kit / 规格写作）要求：结果、范围、验证命令、禁区写清。

## 2. `acceptance` 对象 schema

每个任务可选（**强烈建议** pending 即填；**强制** `in_progress`）：

```json
"acceptance": {
  "status": "draft | ready | blocked",
  "given": ["前置条件…"],
  "when": ["用户/系统动作…"],
  "then": ["可观察结果…"],
  "verify_commands": ["bash …", "./gradlew …"],
  "forbidden": ["禁止改动的路径或行为…"],
  "definition_of_done": ["勾选式完成定义…"],
  "open_questions": ["未知产品点，须人类确认后才能编码…"]
}
```

| 字段 | 必填时机 | 说明 |
|------|----------|------|
| `status` | 建议 | `draft`=占位；`ready`=可编码；`blocked`=缺人类答案 |
| `given` / `when` / `then` | ready 时至少各 1 条 | BDD 式；勿编造未确认产品行为 |
| `verify_commands` | ready 时 ≥1 | 须真实可跑（优先模块测试 / local-ci） |
| `forbidden` | 建议 | 黄金向量、Room schema、密钥等 |
| `definition_of_done` | **in_progress 强制** | 非空字符串列表；校验 GOV-PROTO-004 |
| `open_questions` | 信息不足时 | 有未答问题则 `status=blocked`，禁止开工 |

## 3. 工作流

1. 认领任务前：读 AC；若 `draft`/`blocked` 或缺失 → 先补 AC 或问人类，**不写业务代码**。
2. 将 `status` 改为 `in_progress` 前：`acceptance.status=ready` 且 `definition_of_done` 非空。
3. 标记 done 前：逐条执行 `verify_commands`，对照 `then` / DoD。
4. PR：勾选「AC 已覆盖」（见 `.github/PULL_REQUEST_TEMPLATE.md`）。

## 4. 与既有完成任务的关系

看板中可能存在「标题类似、状态已 done」的旧任务（如 T-033 新手引导 vs T-045 补全）。新 AC 必须写明是**缺口补全 / 回归 / 新切片**，并指向已有 DEC 或代码路径；禁止假装从零实现已落地功能。

## 5. 示例（摘要）

见 `.ai/tasks.json` 中 T-045、T-046、T-049、T-058。

## 来源

DEC-048；对照 `docs/guides/GC-multi-agent-collab.md`。
