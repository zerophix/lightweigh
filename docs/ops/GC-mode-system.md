# GC-mode-system: AI 工作模式统一规范 (DEC-077)

## 两个概念，别混 (DEC-083)

| | 工作模式 | 治理暂停 |
|---|---|---|
| 命令 | `wf-mode.sh switch <mode>` | `wf-mode.sh suspend [2h]` / `resume` |
| 状态文件 | `.ai/current-mode.json`（HMAC 签名 + TTL） | `.ai/gov-mode.json`（无签名，仅计时） |
| 环境变量 | `GOV_MODE`（只读，宽松值不可由环境变量声明） | `GOV_SUSPEND=1` |
| 作用 | 决定跳过哪些检查、记审计、合并时补票 | 让钩子整体放行，不产生审计 |
| 历史 | 沿用 `GOV_MODE` | 旧写法 `GOV_MODE=off`（已废弃，仅兼容并告警） |

**为什么分开**：`GOV_MODE` 曾被同时用作「off」和「explore/docs/…」，一个变量两种语义。
更糟的是 `GOV_MODE=explore git commit` 能绕过 HMAC 和 TTL 直接跳过编译。
现在 `gov_mode_sanitize_env` 在 pre-commit / pre-push / local-ci 入口清洗掉未签名的宽松值。

## 规范模式词表

| Mode | 来源 | TTL | 行为 | Hook 影响 |
|---|---|---|---|---|
| `explore` | `wf-mode.sh switch explore` | 2h default | 跳过编译, 允许 audit-only bypass | pre-commit 跳过 gradle, pre-push audit-only, local-ci Lane A |
| `docs` | `wf-mode.sh switch docs` | 2h default | 跳过编译, 文档校验 | 同 explore |
| `test` | `wf-mode.sh switch test` | 4h default | 跳过编译, 仅 pytest | local-ci skip gradle |
| `code` | `wf-mode.sh switch code` / `deliver` alias | permanent | 标准全量 (default) | 无影响 |
| `gov` | `wf-mode.sh switch gov` / `audit` alias | permanent | 严格校验 | local-ci full |
| `dev` | 无模式文件 (fallback) | permanent | 标准全量 (default) | 无影响 |

## 合并时强制 (gov-merge.sh step 7)
- 始终 `GOV_MODE=dev` + `GOV_LANE=C` + `GOV_CI_FULL=1`
- 删除工作区 `.ai/current-mode.json`
- 确保 explore 提交必须通过完整 CI

## 共享文件锁 (wf-lock.sh)
- `file-acquire <path> --mode docs --ttl 240`
- `file-release <path>`
- `file-check <path>`
- pre-commit 检查: chore/board-* 分支上的 shared files 必须持锁
- 锁文件: `.ai/locks/<sha256(path)>.lock`

