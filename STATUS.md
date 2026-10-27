# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-27T14:26:04Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | 594c861 Merge pull request #73 from zerophix/chore/board-fix-notion-governance-gaps |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## 最近动态

最近 5 次提交：

```
594c861 Merge pull request #73 from zerophix/chore/board-fix-notion-governance-gaps
798ae49 chore(lifecycle): sync derived docs after merging master
81faa4b chore(lifecycle): sync derived docs after merging master
bef0610 fix(board): add acceptance criteria to 21 pending tasks
cba376a fix(governance): close Notion-governance gaps
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 82 |
| 设计笔记 (NOTE) | 13 |
| 开发指南 (GC) | 10 |
| 技术参考 (REF) | 4 |

最近决策：

- [DEC-082](docs/ai/decisions/DEC-082.md): DEC-082: 门禁静默失效 —— hooksPath 必须锚定主树
- [DEC-081](docs/ai/decisions/DEC-081.md): DEC-081: UI 接入波 —— 呈现式屏接入 DDD 数据流
- [DEC-080](docs/ai/decisions/DEC-080.md): DEC-080: 成本可见化 —— 人类等待时间必须可视

最近笔记：

- [NOTE-20261027-002](docs/notes/NOTE-20261027-002.md): 反馈处理 SLA 与回复模板
- [NOTE-20261027-001](docs/notes/NOTE-20261027-001.md): UI 已铺开、DDD 层未接入：缺口清单与接入计划
- [NOTE-20261026-001](docs/notes/NOTE-20261026-001.md): AI 开发流全景：现状解剖、开源对照、通用化设计与分层优化

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-27T14:26:04Z sha=594c861 branch=master [gradlew :app:testDebugUnitTest:ok,pytest tests/unit (full):ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok,gov-a11y-check:ok,gov-hook-doctor:ok,gov-reachability-check:ok] |
| GitHub Actions | 失败 | ci @ 2026-10-27T11:43:03Z sha=869c473 https://github.com/zerophix/lightweigh/actions/runs/40498217127 |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-27T14:26:04Z sha=594c861 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

