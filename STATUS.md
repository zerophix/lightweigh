# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-23T12:04:56Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | feat/privacy-auth-flow |
| 最后提交 | 2f606e3 chore(status): local-ci evidence for privacy auth flow |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-23 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: done-line | 决策: reason-line

**下一步：**

> next-line

## 最近动态

最近 5 次提交：

```
2f606e3 chore(status): local-ci evidence for privacy auth flow
2d12151 Merge remote-tracking branch 'origin/fix/worktree-aware-governance' into feat/privacy-auth-flow
fd8d448 chore(governance): register privacy test and sync task graph counts
777b071 feat(privacy): add five-state privacy authorization flow
23facbd fix(governance): strip hook git env from the test runner (DEC-062)
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 62 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 9 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-062](docs/ai/decisions/DEC-062.md): DEC-062: 治理检查改为 worktree 感知，使 DEC-050 的并发隔离真正可用
- [DEC-061](docs/ai/decisions/DEC-061.md): DEC-061: Wave 1 新屏以呈现式组件交付，静态文案与运行时读数分离
- [DEC-060](docs/ai/decisions/DEC-060.md): DEC-060: 僵尸任务规则改为按停滞时长判定，不再拦截认领中的任务

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-23T11:20:40Z sha=2d12151 branch=feat/privacy-auth-flow [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 失败 | ci @ 2026-10-23T11:22:17Z sha=2f606e3 https://github.com/zerophix/lightweigh/actions/runs/39945589891 |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-23T11:20:40Z sha=2d12151 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

reason-line

