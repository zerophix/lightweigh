---
name: gov-mode
description: >
  AI 工作模式管理 — explore/docs/test/code/gov/dev 五档模式切换 + 审计追踪。
  替换 --no-verify 取巧: 切换到 explore 模式即可合法跳过编译,
  系统自动记录审计日志, 合并时强制回到 dev 模式执行全量 CI。
  触发词: /mode、/governance、/切换模式、/探索模式、/doc-模式、/dev-模式。
---

# AI 工作模式系统 (gov-mode)

**本 skill 不靠文字约束, 靠模式文件驱动。** 切换模式 → `.ai/current-mode.json` 写入 → hooks 自动读取 → 行为调整。

## 规范模式词表

| Mode    | TTL     | Hook 影响                            | 典型场景     |
|---------|---------|--------------------------------------|--------------|
| explore | 2h      | pre-commit 跳过 gradle, pre-push audit-only, local-ci Lane A | 原型开发, 探索 |
| docs    | 2h      | 同 explore, 额外文档结构校验         | 写文档       |
| test    | 4h      | local-ci skip gradle, 仅 pytest      | 调试测试     |
| code    | permanent | 标准治理 (default)                 | 功能开发     |
| gov     | permanent | 严格治理 (default)                 | 治理脚本开发  |
| dev     | permanent | 标准治理 (fallback, 无模式文件)     | 合并时强制   |

**别名**: `deliver → code`, `audit → gov` (向后兼容)

## 快速入口

```bash
# 切换探索模式 (2h TTL)
bash scripts/workflow/wf-mode.sh switch explore

# 查看当前模式
bash scripts/workflow/wf-mode.sh status

# 回到标准模式
bash scripts/workflow/wf-mode.sh switch dev

# 合并时自动: gov-merge.sh 强制 GOV_MODE=dev
```

## 合并时强制 (gov-merge.sh)

- 始终设置 `GOV_MODE=dev + GOV_LANE=C + GOV_CI_FULL=1`
- 删除工作区 `.ai/current-mode.json`
- 确保 explore 提交通过完整 CI (never merged with audit-only)

## 共享文件锁 (wf-lock.sh)

```bash
bash scripts/workflow/wf-lock.sh file-acquire "AGENTS.md" --mode docs  # 持锁 240min
bash scripts/workflow/wf-lock.sh file-check "AGENTS.md"                  # 查询
bash scripts/workflow/wf-lock.sh file-release "AGENTS.md"                 # 释放
```

- pre-commit: chore/board-* 分支共享文件必须持锁
- pre-push: defense-in-depth, 检查 push commits 中的共享文件锁状态

## 审计追踪

- explore/docs 模式的 `--no-verify` 记录到 `.ai/audit/bypass.log`
- 日志格式: `session_id|mode|commit_sha|timestamp|justification`
- `GOV_BYPASS_POLICY=block`: pre-push 拒绝 explore 提交 (force full audit)

## Mode-aware 自动驾驶

`wf-autopilot.sh start <T-XXX>` 会根据任务类型自动选模式:

| 任务类型关键词 | 自动模式 |
|----------------|----------|
| prototype / 原型 / 探索 | explore |
| bugfix / bug / fix | code |
| doc / documentation | docs |
| test | test |
| gov / governance | gov |

如需手动切换: `wf-mode.sh switch <mode>`

## 来自

- DEC-077
- docs/ops/GC-mode-system.md
- tests/unit/test_gov_mode.py (16 tests)
