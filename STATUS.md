# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-27T02:19:11Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | 4014058 fix(device): remove unused QkTextField import |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## 最近动态

最近 5 次提交：

```
4014058 fix(device): remove unused QkTextField import
cfbbcae chore(deps): merge dependabot/github_actions/actions/setup-python-7
0d45f4a chore(deps): merge dependabot/gradle/androidx.core-core-ktx-1.19.1
714efda chore(deps): merge dependabot/gradle/com.google.devtools.ksp-2.3.12
4aca2cc chore(deps): merge dependabot/gradle/org.json-json-20260814
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 71 |
| 设计笔记 (NOTE) | 11 |
| 开发指南 (GC) | 9 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-071](docs/ai/decisions/DEC-071.md): DEC-071: 原型文案匹配率改为「按任务聚合 + 逐 view」作用域
- [DEC-070](docs/ai/decisions/DEC-070.md): DEC-070: 禁止绕过 —— 用钩子取代自觉
- [DEC-069](docs/ai/decisions/DEC-069.md): DEC-069: worktree 生命周期硬约束 —— 上限、登记、必删

最近笔记：

- [NOTE-20261026-001](docs/notes/NOTE-20261026-001.md): AI 开发流全景：现状解剖、开源对照、通用化设计与分层优化
- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 失败 | local-ci FAIL @ 2026-10-27T02:19:11Z sha=4014058 branch=master [gov-validate:fail,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok,gov-a11y-check:ok,gov-hook-doctor:ok] |
| GitHub Actions | 失败 | ci @ 2026-10-27T02:12:10Z sha=4014058 https://github.com/zerophix/lightweigh/actions/runs/40437047490 |
| 文档校验 | 失败 | 来自 local-ci @ 2026-10-27T02:19:11Z sha=4014058 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

