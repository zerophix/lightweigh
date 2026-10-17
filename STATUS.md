# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-17T12:41:28Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | f46dccb Merge pull request #1 from zerophix/fix/nav-and-cloud-push |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-17 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: CI 首次真正跑通并全绿。定位并修复 KSP 插件解析失败：runner 侧阿里云三个镜像全部 502，而 Gradle 解析遇 5xx 直接终止、不回退后续仓库，排首位的镜像令解析在第一个源即失败；settings.gradle.kts 两个 repositories 块改为按 CI 环境变量分流（CI 只用官方源、本地保留阿里云优先）。KSP 由 2.3.2 校正到公开可取的 2.3.9。PR #1 合并入 master，master 推送触发 run 39027280045，build-and-guard / docs-validate / lifecycle-audit 三 job 全绿。DEC-031（含根因更正）与 DEC-032 落盘并入索引，DevNotes 与 README 反思同步，DEC-027 方案评分表整形。本地切回 master、已合并分支 fix/nav-and-cloud-push 本地与远端均删除。 | 决策: 关键取舍三处。其一，最初把 CI 失败判为「KSP 2.3.2 在公开仓库不存在」，钉到 2.3.9 后 CI 报同一个错，该假设被否证；真因是仓库可用性与 Gradle 的失败语义（遇 5xx 终止而非回退），证据是 runner 侧逐源探针（阿里云 502 / Maven Central 200）与 --info 日志里的 HTTP 502 三连重试。其二，修法选「按 CI 环境变量分流」而不是「官方源排前面」或「删镜像」：后两者会推翻文件里已记录的国内网络取舍（google() 直连超时会拖垮构建），为救 CI 牺牲本地体验属拆东墙补西墙。其三，保留 2.3.9 而非回退 2.3.2：2.3.9 已被 runner 探针证实可从 Maven Central 取到（HTTP 200），2.3.2 的可获取性始终依赖本机缓存。

**下一步：**

> 为本次 KSP/Room 版本变更补设备级验证：在模拟器跑 MigrationTest 与一次 Room 读写冒烟，并把结论登记进 DEC-032 的 Assumptions

## 最近动态

最近 5 次提交：

```
f46dccb Merge pull request #1 from zerophix/fix/nav-and-cloud-push
4443e4b fix(ci): skip aliyun mirrors on runners
e23a5a7 chore(ci): probe plugin repositories from the runner
3def32d fix(build): pin ksp to a published version
7053571 docs(readme): record pre-push reflection gate treadmill
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 32 |
| 设计笔记 (NOTE) | 7 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-032](docs/ai/decisions/DEC-032.md): DEC-032: CI 环境跳过阿里云镜像（Gradle 遇 5xx 不回退后续仓库）
- [DEC-031](docs/ai/decisions/DEC-031.md): DEC-031: KSP 钉到公开仓库存在的 2.3.9（修复 CI 插件解析失败）
- [DEC-030](docs/ai/decisions/DEC-030.md): DEC-030: 构建层启用 core library desugaring（取代 @RequiresApi 与 min

最近笔记：

- [NOTE-20261017-001](docs/notes/NOTE-20261017-001.md): 参考项目功能移植对照审查
- [NOTE-20261016-002](docs/notes/NOTE-20261016-002.md): DataRefreshBus 扩展边界
- [NOTE-20261016-001](docs/notes/NOTE-20261016-001.md): NOTE-20261016-001: 云端表 v2 增量 DDL（阶段 1 契约升级）

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 构建 | 跳过 | skipped |
| 文档校验 | 就绪 | 运行: bash scripts/governance/gov-validate.sh |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

关键取舍三处。其一，最初把 CI 失败判为「KSP 2.3.2 在公开仓库不存在」，钉到 2.3.9 后 CI 报同一个错，该假设被否证；真因是仓库可用性与 Gradle 的失败语义（遇 5xx 终止而非回退），证据是 runner 侧逐源探针（阿里云 502 / Maven Central 200）与 --info 日志里的 HTTP 502 三连重试。其二，修法选「按 CI 环境变量分流」而不是「官方源排前面」或「删镜像」：后两者会推翻文件里已记录的国内网络取舍（google() 直连超时会拖垮构建），为救 CI 牺牲本地体验属拆东墙补西墙。其三，保留 2.3.9 而非回退 2.3.2：2.3.9 已被 runner 探针证实可从 Maven Central 取到（HTTP 200），2.3.2 的可获取性始终依赖本机缓存。

