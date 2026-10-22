---
title: 会审报告索引
date: 2026-10-16
type: index
---

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
| 2026-10-22 | gov-scripts-audit | [grokbot__20261022_112830](20261022_gov-scripts-audit/grokbot__20261022_112830.md) | 12 项（High 4 / Medium 5 / Low 3） | 0 | 0/12，新发现待处理（钩子双轨、code-check 双缺失）；含对 10-21 审计的 10-22 更正 |
| 2026-10-22 | ai-dev-fit | [grokbot__20261022_113000](20261022_ai-dev-fit/grokbot__20261022_113000.md) | 9 项（High 4 / Medium 3 / Low 2） | 0 | 文档对齐与蒸馏规则已由 DEC-045 落地；剩余 P0：CI code-check、开源/分支保护 |
| 2026-10-21 | process-weight | [grokbot__20261021_105035](20261021_process-weight/grokbot__20261021_105035.md) | 10 项（High 3 / Medium 4 / Low 3） | 0 | 0/10，新发现待处理（流程减负三阶段方案） |
| 2026-10-21 | m2-prereq | [conclusion](20261021_m2-prereq-checklist/conclusion.md) | 9 项（Critical 3 / High 3 / Medium 3） | 3 | 待处理（M2 开工前置清单） |
| 2026-10-20 | ai-dev-readiness | [grokbot__20261020_211733](20261020_ai-dev-readiness/grokbot__20261020_211733.md) | 11 项（High 3 / Medium 5 / Low 3） | 0 | 0/11，新发现待处理（P0：F-001 收口未提交改动） |
| 2026-10-20 | ddd-architecture | [opencode__20261020_143000](20261020_ddd-architecture/opencode__20261020_143000.md) | 10 项（F-010 已撤回，有效 9） | 1 Critical **已修** | **9 项全部关闭**：6 项已修（F-001~006）、3 项评估后不采纳（F-007/008/009，理由见 DEC-034 组 E/F） |
| 2026-07-10 | template-review | [claude__20260710_082000](20260710_template-review/claude__20260710_082000.md) | 22 项 | 2 Critical 已修复 | 86%（19/22，剩余 F-009 Medium 待定制） |
| 2026-07-11 | project-status | [openenode__20260711_233112](20260711_project-status/openenode__20260711_233112.md) | 8 项 | 0 | 新发现待处理 |

## 统计汇总

| 维度 | 值 |
|------|-----|
| 总评审次数 | 8 |
| 总发现数 | 91（12 + 9 + 10 + 9 + 11 + 10 + 22 + 8），其中 ddd-architecture 的 F-010 经三轮核验撤回，有效发现 90 |
| 已修复 | 25（template-review 19 + ddd-architecture 6：F-001~F-006）；另有 3 项评估后不采纳（F-007/008/009）、1 项撤回（F-010）；**DEC-045 关闭 ai-dev-fit 文档/蒸馏两项阻塞**（未计入上列历史「已修复」基数，避免与分项报告口径纠缠） |
| 新发现待处理 | **约 49**（gov-scripts-audit 12 + ai-dev-fit 余项 + process-weight 10 + ai-dev-readiness 11 + m2-prereq 9；部分与前序重叠） |
| 整体修复率 | 历史口径约 **36%**（25/69）；ddd-architecture 一项已 100% 关闭 |
| 最新进展 | 2026-10-22 DEC-045：文档事实对齐（BLE/云已落地）、蒸馏规则取消强制反思、治理脚本审计与 AI 适配度评审入库；剩余人类项：推远程、开源/分支保护、CI 挂 code-check、钉死 simplified hook 安装 |

> **口径说明**：该报告初版统计为 `critical: 2`，但 F-002 已于二轮核验降级为 Medium
> （`SyncTrigger` 属 P0 产品决策的显式实现），三轮核验同步更正为 1 项 Critical。
> F-001 虽标 Critical，但其修复建议经评估未采纳，改以事件类型化达成同等目标（见 DEC-034）。

## 流程说明

1. 触发评审：`/评审` 或 `/review` 命令
2. 各 AI 独立审查，写入 `{agent}__{timestamp}.md`
3. 所有报告包含 front-matter（agent/model/timestamp/finding_count 等）
4. 可选：写入 `conclusion.md` 作为收敛结论
5. 修复完成后更新对应报告中的修复状态表
