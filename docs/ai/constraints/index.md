# 约束规则

> **职责：** 定义行为边界（Behavior Boundary）

---

## 使用规范

1. **约束必须可检查** — 每条约束必须有明确的是/否判定
2. **约束不可随意修改** — 修改必须通过决策日志记录
3. **AI 首次执行前必须读取此文档**

---

<!-- 在此添加项目专属约束 -->

## 架构约束

| 规则ID | 描述 | 违反后果 |
|--------|------|---------|
| ARCH-001 | 新增模块必须经过架构评审 | 拒绝合入 |

## 流程约束

| 规则ID | 描述 | 适用范围 |
|--------|------|---------|
| FLOW-001 | AI 不能直接修改 PRD/架构 | 所有 AI |
| FLOW-002 | 决策日志必须追加写 | AI + 人类 |

## 反思质量约束

| 规则ID | 描述 | 违反后果 |
|--------|------|---------|
| REFLECT-001 | 每次提交必须包含反思（lessons），通过 context-cache.sh save handoff lessons=... 写入 | 提交被拦截 |
| REFLECT-002 | 反思内容 ≥50 字，含信号词（错/坑/教训/避免/本应 等） | R0 校验 FAIL |
| REFLECT-003 | 反思不能与上次提交相同（禁止复用旧反思） | R0 校验 FAIL |

## 文件命名约束

| 规则ID | 目录 | 命名模式 | 说明 |
|--------|------|---------|------|
| NAME-001 | `guides/` | `GC-{slug}.md` | 指南正文文件，slug 为小写英文字母/数字/连字符 |
| NAME-002 | `reference/` | `REF-{slug}.md` | 参考/协议文档，slug 为小写英文字母/数字/连字符 |
| NAME-003 | `reference/` | `index.md` | reference 目录必须保留 index.md 作为索引 |
| NAME-004 | `notes/` | `NOTE-YYYYMMDD-NNN.md` | 设计笔记，日期+3位编号 |
| NAME-005 | `archive/` | `ARCH-{slug}.md` | 旧文档归档，slug 为小写英文字母/数字/连字符 |
| NAME-006 | `ai/decisions/` | `DEC-XXX.md` | 决策日志，3位数字编号 |
| NAME-007 | `ai/lifecycle/` | `NN_Name.md` | 生命周期文档，2位数字+下划线+英文名 |
| NAME-008 | `ai/reviews/` | `{date}_{topic}/{agent}.md` | 评审报告，日期+主题子目录内 |
