<!--
  PR 模板 · 依据 GC-multi-agent-collab §7「PR 合入清单」与 DOC-需求到编码链路 §4

  规则：「门禁」全部勾选 = 可合入；未全勾 = 视为不可合入。
        无 GitHub Pro、无 required checks 时，勾选是唯一硬门槛。

  填写要求：
  - 以 <> 或空行占位的，提交前替换；确实无关的写「无」
  - 不要在本模板任何位置填写放行标记，除非 CI 的 guard 明确要求
  - 本模板刻意不含放行关键词字面量，也不含 DEC 加三位数字的字面量；
    否则相应 guard 会把「未填写」误判为「已放行」而永久静默通过
-->

## 关联（追溯 ID）

| 项 | 填写 |
|----|------|
| 关联任务 | `T-XXX` 或 无（批次 PR 写 `B-XXX` 并列出所有任务） |
| 关联决策 | `DEC-NNN` 或 无 |
| 上层文档 | Notion URL / 仓库路径 / 无 |
| 原型参考 | `design/prototypes/interactive/<file>.html` + view 列表 / 无 |

<!-- 关联决策按 DEC-NNN 三位数字格式填写（gov-schema-guard 据此识别）。
     协议、算法、Room schema、同步语义、治理门禁变更 → 必填，不接受「无」。 -->

## 变更类型

- [ ] 任务 AC 覆盖的实现
- [ ] 缺陷修复
- [ ] 文档 / 治理
- [ ] 协议、算法、Room schema 或同步语义（必填关联决策）

## 范围

- 模块：`M#` 或 模块名
- 屏 / 流程：`S-...` 或 原型 view 列表
- Out of scope（明确不做什么）：

## 主要变更


## 影响面

（改动了哪些模块、是否触碰共享组件、是否影响进行中的其他任务）


## 验收 Done when

<!-- 从 PRD / 专文原文复制 AC，禁止改写成自己的理解 -->

- [ ] `AC-...`：
- [ ] 测试：（命令或用例 ID）

## 批次 PR 额外验收（仅批次 PR 填写）

- [ ] 批次内所有任务 `gov-task-done.sh T-xxx` 通过
- [ ] 批次 PR body 列出所有任务 ID 与描述

## 门禁（全部勾选方可合入）

- [ ] `bash scripts/governance/gov-local-ci.sh` 全绿，摘要粘贴在下方
- [ ] `python3 -m pytest tests/unit -q` 通过
- [ ] 未静默改黄金向量 / Room schema（有改已在关联决策写旧新对照与迁移）
- [ ] 未用 `--no-verify` 绕过钩子
- [ ] 多会话已执行 `bash scripts/session/ses-claim-staged.sh` 声明归属
- [ ] 文档变更：`gov-validate` 违规 0 项（含 GOV-DOCS-001~005）
- [ ] 原型映射任务（T-072~T-105）：`gov-prototype-verify.py <task> --strict` 通过

```text
gov-local-ci 摘要：
（粘贴末尾 "通过: N 失败: N" 与 "PASS gov-local-ci" 两行）
```

## 自检

- [ ] 标题形如 `type(scope): subject`，英文，≤72 字符，不以句号结尾
- [ ] 未在 Notion 与仓库各留一份互相矛盾的结论（归属规则见 GC-decision-flow 第 0 层）
- [ ] 改了 `.ai/tasks.json` 计数则已同步 `docs/ai/lifecycle/03_TaskGraph.md`
- [ ] 新增 `docs/guides/GC-*.md` 已收进 `docs/guides/index.md`

## 门禁要求放行时才填（默认留空）

<!-- 仅当 CI 的 gov-test-guard 或 gov-schema-guard 明确输出「放行」要求时填写。
     放行标记必须从 CI 输出原样复制，自行编写等同绕过门禁。 -->

- 无测试放行（标记 + 理由）：
- Schema 变更放行（标记 + 理由）：
