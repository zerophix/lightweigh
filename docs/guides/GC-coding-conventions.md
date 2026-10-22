# 编码约定（Android / AI）

> 模块地图、命令矩阵、屏幕四件套、禁区。细节决策以 DEC 与 `docs/architecture/` 为准。DEC-048。

---

## 1. 模块地图

| 模块 | 类型 | 职责 |
|------|------|------|
| `:domain` | 纯 JVM | 实体、端口、领域规则；**零** Android / Room / UI |
| `:application` | 纯 JVM | 用例 / 应用服务；依赖 domain 端口 |
| `:infrastructure` | Android 库 | 端口实现：Room、网络、Preference、蓝牙适配 |
| `:app` | Android 应用 | 组合根、`feature/*` UI、ArchUnit |

依赖方向：`app → application → domain ← infrastructure`（倒置由 app 组装）。

包根：`com.xcwk.scalehub`。

## 2. Gradle 命令矩阵

```bash
./gradlew :domain:test
./gradlew :application:test
./gradlew :infrastructure:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew build
bash scripts/governance/gov-validate.sh
bash scripts/governance/gov-custom-checks.sh
bash scripts/governance/gov-local-ci.sh    # 合入权威：build + validate + custom-checks
```

优先跑变更模块测试，合入前跑 `gov-local-ci.sh`。

## 3. 屏幕四件套（DEC-021）

新页面 = 新目录 + 导航表一行：

```
app/src/main/kotlin/com/xcwk/scalehub/feature/<name>/
  <Name>Screen.kt      # Compose UI，零业务
  <Name>ViewModel.kt   # 编排用例 / 读模型
  <Name>UiState.kt     # 不可变状态
  <Name>Route.kt       # 入口 / 副作用边界
```

UI 不直访问 Room / SDK；经 ViewModel → application/domain 端口。

既有 feature 目录示例：`measure`、`report`、`trend`、`history`、`device`、`guide`、`habit`、`ai`、`auth`、`settings`、`mine`、`member`、`record`。

## 4. Room / 迁移

- 当前 `AppDatabase` **version 7**；schema 快照在
  `infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/`。
- 改实体：**version+1**、写 `Migration`、导出 schema JSON **入库**、补静态对拍/迁移测试。
- 禁止静默改 schema 文件「只为让测试绿」。

## 5. 黄金数据与协议禁区

| 资产 | 路径线索 | 规则 |
|------|----------|------|
| 体脂等黄金向量 | `domain/src/test/resources/golden/` | 改向量须有 DEC + 旧/新对照说明 |
| Room schema | `infrastructure/schemas/...` | 同上 + version 纪律 |
| 协议帧 / 算法 | `domain` 协议与算法包 | 先红测再改；禁止删断言凑绿 |

PR 模板要求：黄金 / schema 变更必须显式说明。

## 6. 测试期望

- 领域 / 用例：JVM 单测优先。
- UI：ViewModel 单测；ArchUnit 在 `:app` 测试中守分层。
- 真机 / 仪器：人类验收（tasks 中标注）；AI 不伪称已真机通过。

## 7. codebase-memory

- **用户级 MCP**（本机配置，不入库）：查「代码在哪、怎么连」。
- **仓内 DEC / 架构 / tasks.json**：查「为什么、当前做什么」。
- 文档与代码冲突：先信代码与测试，再立 DEC 修文档（DEC-045）。

## 8. 相关文档

- 决策流转：`GC-decision-flow.md`
- 多 AI：`GC-multi-agent-collab.md`
- 任务 AC：`GC-task-acceptance.md`
- 生命周期步骤：`docs/ai/guides/workflow.md`
- 架构：`docs/architecture/index.md`（经 promote）

## 来源

DEC-048；架构事实来自 lifecycle / DEC-021/045 等。
