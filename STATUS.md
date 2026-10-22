# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-22T04:38:15Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | b5adac7 Merge pull request #15 from zerophix/chore/ses-worktree |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | unknown-ai |
| 时间 | 2026-10-22 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: DEC-050：ses-worktree.sh + hooks show-toplevel；已合入 PR #15 | 决策: 共享 hooks 在 worktree 须用 show-toplevel；包装脚本降低误用

**下一步：**

> 在 Android 真机安装 APK 验证

## 最近动态

最近 5 次提交：

```
b5adac7 Merge pull request #15 from zerophix/chore/ses-worktree
6411fed feat(session): add ses-worktree and fix hook ROOT for worktrees
f5002af Merge pull request #11 from zerophix/chore/dec049-handoff
ab19b70 chore(session): handoff after DEC-049 merge
4c3a19f Merge pull request #6 from zerophix/chore/ci-dependabot-gitleaks
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 50 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 8 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-050](docs/ai/decisions/DEC-050.md): DEC-050: Worktree 脚本与 hooks 工作树 ROOT
- [DEC-049](docs/ai/decisions/DEC-049.md): DEC-049: 特性分支 CI、Dependabot、本地密钥扫描
- [DEC-048](docs/ai/decisions/DEC-048.md): DEC-048: AI 开发 P0 — 任务 AC、短 AGENTS、PR 合入清单

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 构建 | 跳过 | skipped |
| 文档校验 | 就绪 | 运行: bash scripts/governance/gov-validate.sh |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

共享 hooks 在 worktree 须用 show-toplevel；包装脚本降低误用

