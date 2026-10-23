# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-23T06:55:52Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | cursor/room-schema-v8-dec-s-e5ab |
| 最后提交 | f7574ad feat(room): migrate AppDatabase to v8 for signed schema |
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
f7574ad feat(room): migrate AppDatabase to v8 for signed schema
04af959 Merge pull request #29 from zerophix/fix/chipsea-name-param
bc3efc8 chore(status): local-ci evidence after DEC-056
b23d5c5 docs(decisions): index DEC-056 for chipsea deviceName match
16fb1cc fix(ble): pass scan callback deviceName into Chipsea match (DEC-056)
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 58 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 8 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-058](docs/ai/decisions/DEC-058.md): DEC-058: Room v8 对齐已签库表目标态
- [DEC-057](docs/ai/decisions/DEC-057.md): DEC-057: 需求到编码链路作为任务验收锚点
- [DEC-056](docs/ai/decisions/DEC-056.md): DEC-056: 扫描回调 deviceName 参与 Chipsea 混合协议识别

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-23T06:55:52Z sha=f7574ad branch=cursor/room-schema-v8-dec-s-e5ab [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 进行中 | ci @ 2026-10-23T06:52:31Z sha=f7574ad https://github.com/zerophix/lightweigh/actions/runs/39914173753 |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-23T06:55:52Z sha=f7574ad |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

只改活跃文档与流程说明；历史 DEC/reviews/inbox 与轻刻绿等历史叙述保留；applicationId com.xcwk.scalehub 不变。

