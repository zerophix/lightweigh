# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-24T03:01:43Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | chore/track-html-screens |
| 最后提交 | c1e6cf4 chore(design): track 98 static html screen exports under design/prototypes/html-screens |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-23 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: T-102 交付并开 PR（未合）：
- 新增 feature/sync/NetworkStatus.kt、ServiceStatus.kt：呈现式组件，网络/服务探测经 fun interface（NetworkAvailabilityProbe / ServiceAvailabilityProbe）由调用方注入，状态经 stateOf() 单向进不可变 UiState，Screen 零系统网络依赖；共用版式 SyncStatusPage 内联 NetworkStatus.kt（T-102 限定只新建两文件，不开第三文件）；文案常量 NetworkStatusText / ServiceStatusText 逐字取自原型 08 v-network / v-service。
- 新增 app/src/test/kotlin/com/xcwk/scalehub/feature/sync/NetworkServiceStatusTest.kt（9 条）：文案逐字断言、探测回调建状态、源码守卫（去注释后无 ConnectivityManager / android.net、无 Color(0x..)、无 NN.dp、含 Qk 引用）。
- 治理随行：tests/android-test-registry.yaml 登记新测试（GOV-ATEST-005）；take T-102 后 gov-validate --fix 同步 03_TaskGraph pending=57（GOV-DOCS-004）；CI lifecycle-audit 要求源码变更随 DEC + DevNotes，故向 DEC-061 追加「执行记录（2026-10-23 · T-102）」+ 04_DevNotes 变更记录条目。
- 验证：./gradlew :app:testDebugUnitTest 134 通过；python3 -m pytest tests/unit -q 155 通过；gov-prototype-verify.py T-102 --strict 通过（文案匹配率 54% / 54%）；gov-lifecycle-audit.sh --base origin/master PASS；gov-local-ci.sh 6/6 PASS（分支 tip 512a1a3 的干净 clone，安装 hooks 后运行）。
- 提交 8766b80 / 6c3a482 / 512a1a3，ses-claim-staged 逐次声明归属；pre-push 审计 3/3 通过后推送；PR #42 已按模板开立（含 local-ci PASS 摘要与环境说明），未 merge。
- 收件箱 2 条未读已读（20261023-081815 上轮交接、20261022-082510 DEC-056）。 | 决策: 1) 呈现式交付照 DEC-061 先例：AC-N3 的「网络检测经由传入回调」落成 fun interface 注入，检测实现留在调用方，UI 结构上拿不到系统网络服务（单测源码守卫钉死），而非仅靠自觉不 import。
2) 共用版式 SyncStatusPage 放 NetworkStatus.kt 而非新建第三个文件——任务铁律只允许新建 NetworkStatus.kt / ServiceStatus.kt，同包 internal 复用是最小代价的去重（否则两文件同构会被 GOV-CODE-002/003 判重）。
3) 未新开 DEC-062：本树 gov-next-decision 下一号即 062，但该编号已被推送未合入的 fix/worktree-aware-governance 占用，同号多义违 DEC-019 索引唯一、同名文件必冲突；跳号到 063 违 GOV-CONTENT-004 编号连续。故按 DEC-002「执行中追加发现」、DEC-031「更正」的追加先例，把 T-102 执行记录并入 DEC-061（本任务本就是该决策口径的执行）。
4) worktree 内 gov-local-ci 恒有 2 项根检查环境性误报（.git 是文件、hooks 路径按 ROOT/.git/hooks 拼），修复属别人在写的文件（后写者停手），故门禁改在分支 tip 的干净 clone 运行取得 6/6 PASS，pre-push 也在该 clone 通过后推送——全程未用 --no-verify。
5) 文案匹配率 54%：v-network/v-service 短语由两文件分散持有所致，与 DevNotes 问题 7「多文件/薄封装任务系统性偏低」结论一致，全部应有文案实际逐字在位（单测断言）。

**下一步：**

> 跟进在途 PR 与 Actions 故障：Actions 恢复后 gh run rerun 39944412520 复跑 PR #42 checks；各在途分支合入 master 后先跑 gov-validate --fix 同步 03_TaskGraph 计数再把对应任务置 done（T-102 含）。

## 最近动态

最近 5 次提交：

```
c1e6cf4 chore(design): track 98 static html screen exports under design/prototypes/html-screens
fe3e092 Merge pull request #49 from zerophix/chore/wave1-ac-batch-2
116f3ee chore(status): local-ci evidence for ac batch (6/6 PASS)
14c528a chore(tasks): mark merged wave1 tasks completed and sync counts
577eec0 Merge remote-tracking branch 'origin/master' into chore/wave1-ac-batch-2
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
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-24T03:01:43Z sha=c1e6cf4 branch=chore/track-html-screens [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 无数据 | gh 不可用或无最近 run |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-24T03:01:43Z sha=c1e6cf4 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

1) 呈现式交付照 DEC-061 先例：AC-N3 的「网络检测经由传入回调」落成 fun interface 注入，检测实现留在调用方，UI 结构上拿不到系统网络服务（单测源码守卫钉死），而非仅靠自觉不 import。
> 2) 共用版式 SyncStatusPage 放 NetworkStatus.kt 而非新建第三个文件——任务铁律只允许新建 NetworkStatus.kt / ServiceStatus.kt，同包 internal 复用是最小代价的去重（否则两文件同构会被 GOV-CODE-002/003 判重）。
> 3) 未新开 DEC-062：本树 gov-next-decision 下一号即 062，但该编号已被推送未合入的 fix/worktree-aware-governance 占用，同号多义违 DEC-019 索引唯一、同名文件必冲突；跳号到 063 违 GOV-CONTENT-004 编号连续。故按 DEC-002「执行中追加发现」、DEC-031「更正」的追加先例，把 T-102 执行记录并入 DEC-061（本任务本就是该决策口径的执行）。
> 4) worktree 内 gov-local-ci 恒有 2 项根检查环境性误报（.git 是文件、hooks 路径按 ROOT/.git/hooks 拼），修复属别人在写的文件（后写者停手），故门禁改在分支 tip 的干净 clone 运行取得 6/6 PASS，pre-push 也在该 clone 通过后推送——全程未用 --no-verify。
> 5) 文案匹配率 54%：v-network/v-service 短语由两文件分散持有所致，与 DevNotes 问题 7「多文件/薄封装任务系统性偏低」结论一致，全部应有文案实际逐字在位（单测断言）。

