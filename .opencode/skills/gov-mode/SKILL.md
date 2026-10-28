# Gov Mode Skill (工作模式管理)

## 原理

```
/gov-explore     → wf-mode.sh switch explore
                   → .cache/modes/current.json: {mode: explore, expires: +2h}
                   → pre-commit 跳过 4 模块编译
                   → pre-push GOV_BYPASS_POLICY=warn
                   → local-ci 仅 gov-validate.sh

/gov-dev         → 恢复标准治理 (默认)
/gov-deliver     → 强制 gov-task-done + local-ci
/gov-audit       → 禁止所有绕过, 强制 full pytest
```

## 使用

```bash
# 切换模式 (在对话中输入):
/gov-explore     # 快速迭代: 跳过编译, 允许 --no-verify
/gov-dev         # 标准开发 (默认)
/gov-deliver     # 准备合入: 强制 local-ci
/gov-audit       # 治理审计: 禁止绕过

# 查询状态:
/gov-mode status
```

## 模式定义

| 模式 | pre-commit | pre-push | local-ci | 用途 |
|---|---|---|---|---|
| explore | 跳过编译 | 允许 bypass (warn) | gov-validate only | 原型/探索 |
| dev | 全部 12 检查 | 全部 4 检查 | 车道判定 | 标准开发 |
| deliver | 12 检查 + staged-claims | 4 检查 + audit trail | 强制 C 车道 | 准备合入 |
| audit | 12 检查 + 安全扫描 | block 所有 bypass | full + pytest cov=100% | 治理审计 |

## 自动过期

- explore: 2小时后自动回退到 dev
- dev: 8小时后自动回退到 explore
- deliver: 1小时后自动回退到 dev
- audit: 永不过期 (除非手动切换)

## 文件

- `.cache/modes/current.json` — 当前模式 + 过期时间
- `scripts/workflow/wf-mode.sh` — 模式管理脚本
