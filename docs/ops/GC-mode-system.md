# GC-mode-system: AI 工作模式统一规范 (DEC-077)

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

