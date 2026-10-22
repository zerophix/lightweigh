# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-22T07:54:34Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | adc38c2 Merge pull request #27 from zerophix/chore/docs-consistency |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | unknown-ai |
| 时间 | 2026-10-22 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: DEC-055 文档内容一致性治理；PR #27 已合入 master（merge adc38c2 / 9b5d182）。活跃面轻衡/lightweigh、断链、流程 DEC 范围、MkDocs、architecture/reference/changelog 晋升。 | 决策: 只改活跃文档与流程说明；历史 DEC/reviews/inbox 与轻刻绿等历史叙述保留；applicationId com.xcwk.scalehub 不变。

**下一步：**

> 在 Android 真机安装 APK 验证

## 最近动态

最近 5 次提交：

```
adc38c2 Merge pull request #27 from zerophix/chore/docs-consistency
9b5d182 docs(governance): align docs with lightweigh brand (DEC-055)
14a512f Merge pull request #23 from zerophix/chore/ops-debt-p0-p1
4fe4122 chore(status): align STATUS with ops-debt commit
3dc6a30 chore(ops): clear PROTO inbox debt, Dependabot majors, DEC-054 docs
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 55 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 8 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-055](docs/ai/decisions/DEC-055.md): DEC-055: 文档内容一致性治理（lightweigh / 轻衡）
- [DEC-054](docs/ai/decisions/DEC-054.md): DEC-054: 主共享树多会话守卫（强制 worktree）
- [DEC-053](docs/ai/decisions/DEC-053.md): DEC-053: src/main 缺同模块测试的 PR 提醒

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-22T07:52:07Z sha=14a512f branch=chore/docs-consistency [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 进行中 | ci @ 2026-10-22T07:53:02Z sha=adc38c2 https://github.com/zerophix/lightweigh/actions/runs/39743644307 |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-22T07:52:07Z sha=14a512f |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

只改活跃文档与流程说明；历史 DEC/reviews/inbox 与轻刻绿等历史叙述保留；applicationId com.xcwk.scalehub 不变。

