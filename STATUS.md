# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-22T04:42:11Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | chore/status-local-ci-evidence |
| 最后提交 | e0b2d31 Merge pull request #16 from zerophix/chore/dec050-handoff |
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
e0b2d31 Merge pull request #16 from zerophix/chore/dec050-handoff
1f7906e chore(session): handoff after DEC-050
b5adac7 Merge pull request #15 from zerophix/chore/ses-worktree
6411fed feat(session): add ses-worktree and fix hook ROOT for worktrees
f5002af Merge pull request #11 from zerophix/chore/dec049-handoff
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 51 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 8 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-051](docs/ai/decisions/DEC-051.md): DEC-051: STATUS 门禁证据来自 local-ci / Actions
- [DEC-050](docs/ai/decisions/DEC-050.md): DEC-050: Worktree 脚本与 hooks 工作树 ROOT
- [DEC-049](docs/ai/decisions/DEC-049.md): DEC-049: 特性分支 CI、Dependabot、本地密钥扫描

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-22T04:42:10Z sha=e0b2d31 branch=chore/status-local-ci-evidence [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok] |
| GitHub Actions | 无数据 | gh 不可用或无最近 run |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-22T04:42:10Z sha=e0b2d31 |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

共享 hooks 在 worktree 须用 show-toplevel；包装脚本降低误用

