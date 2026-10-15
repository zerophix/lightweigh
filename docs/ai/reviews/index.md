# 会审报告

> 多 AI 评审产出，按日期+主题分目录存储。
> 每人每轮评审写入独立报告，收敛后由人类或收敛会议写入结论。

---

## 目录结构

```reviews/{YYYYMMDD}_{topic}/
├── {agent1}__{YYYYMMDD_HHMMSS}.md ← AI 1 独立报告
├── {agent2}__{YYYYMMDD_HHMMSS}.md ← AI 2 独立报告
└── conclusion.md ← 收敛结论（可选）
```

## 评审记录

| 日期 | 主题 | 报告 | 发现数 | Critical | 修复率 |
|------|------|------|--------|----------|--------|
| 2026-07-10 | template-review | [claude__20260710_082000](20260710_template-review/claude__20260710_082000.md) | 22 项 | 2 Critical 已修复 | 86%（19/22，剩余 F-009 Medium 待定制） |
| 2026-07-11 | project-status | [openenode__20260711_233112](20260711_project-status/openenode__20260711_233112.md) | 8 项 | 0 | 新发现待处理 |

## 统计汇总

| 维度 | 值 |
|------|-----|
| 总评审次数 | 2 |
| 总发现数 | 30（22 + 8） |
| 已修复 | 19 |
| 新发现待处理 | 8（High: 2, Medium: 3, Low: 3） |
| 整体修复率 | 67%（19/30） |
| 最新评审 | 2026-07-11 状态跟进 |

## 流程说明

1. 触发评审：`/评审` 或 `/review` 命令
2. 各 AI 独立审查，写入 `{agent}__{timestamp}.md`
3. 所有报告包含 front-matter（agent/model/timestamp/finding_count 等）
4. 可选：写入 `conclusion.md` 作为收敛结论
5. 修复完成后更新对应报告中的修复状态表
