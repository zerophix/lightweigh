---
title: M2 开工前置条件核验清单（基于 DEC-034 收口后真实代码）
date: 2026-10-21
agent: opencode
model: nemotron-3-ultra
timestamp: 2026-10-21T10:00:00Z
mode: single
type: conclusion
topic: m2-prereq
dimensions: ["云同步地基", "多成员隔离", "测试覆盖", "治理门禁", "文档时效"]
finding_count: 9
critical: 3
high: 3
medium: 3
low: 0
summary: 基于 DEC-034 组 A~F 已收口后的真实代码逐条核验。Domain/Application/Infrastructure 三大架构债（DataRefreshBus、OkokBia 耦合、memberId 隐式、工厂方法、Clock）已全部闭环；CloudBaseGateway/SyncService 已有实现与单测。剩余硬缺口集中在：SyncService 无水位线/对账、多成员同步隔离无测试、全链路端到端测试缺失、master 分支保护未开、AppContainer 暴露 infra 具体类型、文档滞后。
---

# M2 开工前置条件核验清单

> 核验基线：`master` HEAD `e315482`（DEC-034 组 A~F 已合入）
> 核验时间：2026-10-21
> 核验方式：`find/grep/cat` 逐条实证，非引用文档或早期快照

---

## 一、已闭环的架构债（DEC-034 组 A~F 成果确认）

| 原 Finding | 闭环方式 | 证据 |
|------------|----------|------|
| **DataRefreshBus 错层** | 明确**留在 application**，否决下沉方案。理由：application 层编排关注点；下沉 domain 需给纯模型引入协程依赖 | `DataRefreshBus.kt` 类注释 + DEC-034 组 A |
| **SyncTrigger 注入用例** | 移除注入。新增 `SyncOnLocalWrites` 订阅事件流，仅 `LOCAL_WRITES` 集合点火 | `grep SyncTrigger application/service/` 为空；`SyncOnLocalWrites.kt` 存在，`SyncOnLocalWritesTest` 4 测试 |
| **事件类型化** | `Unit` 信号 → `DataChange` sealed 类型（Measurements/HealthLogs/Profile/AccountDataRestored/AccountDeleted/MemberScopeChanged） | `DataRefreshBus.kt` 完整定义 |
| **RoomDeviceRepository → OkokBia 耦合** | 新增 `ScaleDevice.algorithmFor(protocolId, fallback)` 工厂；`GOV-CODE-004` 治理规则防回归 | `grep OkokBia infrastructure/` 为空；`gov-code-check.py:437` 有规则与自检 |
| **MeasureRecord 贫血/缺工厂** | 新增 `MeasureRecord.create()` 静态工厂，封装算法选择 + 时间戳盖章（Clock 注入） | `MeasureRecord.java:94` |
| **Clock 抽象缺失** | `domain/types/Clock.kt` + `SystemClock`；注入工厂与用例 | `Clock.kt` 存在；`RecordMeasurementService` 注入 `Clock` |
| **memberId 隐式注入** | `MeasurementRepository.save(record, memberId: String)` 显式化；`RecordMeasurementService` 注入 `memberId: () -> String` | `MeasurementRepository.kt:16`；`RecordMeasurementService` 构造器 |
| **CloudPorts 三端口零实现** | `CloudBaseGateway.kt` 93 行完整实现（GET/POST/DELETE、分块 40、Prefer: merge-duplicates、JSON 解析） | 文件存在且实现三方法 |
| **SyncService 零落地** | `SyncService.kt` 294 行；`SyncServiceTest` 14 测试；`SyncTriggerTest` 4 测试 | 文件存在 |

**结论**：M2 的**架构地基已就位**——分层清晰、事件驱动同步、领域工厂、显式不变量、端口实现、单元测试全有。

---

## 二、剩余硬缺口（M2 开工前必须闭环）

### F-001 | Critical | SyncService 无水位线/对账/幂等 【硬约束】

**位置：** `application/src/main/kotlin/com/xcwk/scalehub/application/sync/SyncService.kt`

**证据：**
```bash
grep -n "watermark\|SyncState\|水位线\|对账" application/.../SyncService.kt   # 零命中
find . -name "*.kt" | xargs grep -l "SyncState\|watermark" 2>/dev/null | head  # 零结果
```

**影响：**
- 同步重启后无法增量，只能全量（当前 `push()` 语义是「全部待同步行 upsert」）
- 进程死/网络断后，下次启动无对账依据，漏传/重传风险
- 无幂等键验证，云端主键冲突处理依赖 PostgREST `on_conflict`，未验证边界情况

**必须新增：**
1. `SyncStatePo`（Room Entity）：`tableName`, `pushedWatermark`, `pulledWatermark`, `updatedAt`
2. `push()` 成功后原子更新 `pushedWatermark`（本地事务内，或先推后记、崩溃可重推但不丢）
3. `pull()` 按 `pulledWatermark` 增量拉取，合并时按 `updatedAt` LWW
4. 幂等键：`owner_id + cloud_member_id + measured_at`（云端已定义，本地需对齐）
5. 单测：模拟崩溃中断/重复推送/水位线回退，断言不漏/不重/不串

**验收标准：**
- [ ] `SyncStatePo` + DAO + 迁移脚本落地
- [ ] `SyncServiceTest` 新增 ≥4 个测试：崩溃恢复、重复推送幂等、增量拉取、水位线原子性
- [ ] 全链路测试：本地写 → DataChange → SyncOnLocalWrites → SyncTrigger → SyncService.push → CloudBaseGateway.upsert → 云端确认 → 水位线更新

---

### F-002 | Critical | 多成员同步隔离无测试

**位置：** `application/src/test/kotlin/com/xcwk/scalehub/application/sync/SyncServiceTest.kt`（14 测试，无一覆盖成员切换）

**证据：** `grep -n "memberId\|member" SyncServiceTest.kt` 无隔离相关断言

**影响：**
- 用户 A 记录 10 条 → 切换用户 B → 触发同步 → **A 的数据可能被推到 B 的云端归属**
- 恢复账号（pull）时，B 的数据可能覆盖本地 A 的数据

**必须新增测试场景：**
```kotlin
@Test fun `用户切换后旧成员数据不被新用户同步任务上传`()
@Test fun `pull 时云端多成员数据按 cloud_member_id 正确归属到本地 memberId`()
@Test fun `MemberScopeChanged 事件触发全量重查但不重复推云`()
```

**验收标准：**
- [ ] 3 个隔离测试全绿
- [ ] `SyncService.push()` 内部查询已按当前 `activeMemberId` 过滤（验证 `memberId` 传递链）

---

### F-003 | Critical | 全链路端到端测试缺失

**位置：** `app/src/androidTest/`（零文件）；`infrastructure/src/test/`（仅 `CloudBaseGatewayTest` 离线测试）

**证据：**
```bash
find app -path "*androidTest*" -name "*.kt"   # 零结果
find infrastructure -path "*test*" -name "*IntegrationTest*"   # 零结果
```

**影响：**
- 「本地写 → 事件 → 订阅点火 → SyncService → CloudBaseGateway → HTTP → 云端」整条链路无自动化验证
- 靠人工点点，上线必出事（Token 过期、网络抖动、分块边界、JSON 解析异常全靠运行时才发现）

**必须新增：**
1. **JVM 集成测试**（`application/src/test` 或新建 `integrationTest` source set）：注入假 `CloudSessionPort` + 假 `CloudDataPort` + 内存仓储，跑通全链路
2. **androidTest 冒烟**（模拟器）：真 `CloudBaseGateway` + 测试环境账号，验证 HTTP 往返
3. 至少覆盖：正常链路、Token 失效、网络超时、分块边界（39/40/41 条）、JSON 坏数据

**验收标准：**
- [ ] JVM 集成测试 ≥5 个场景全绿
- [ ] androidTest 冒烟在 CI `android-test` 工作流跑通（手动触发一次，结果记入 DEC）
- [ ] `.github/workflows/android-test.yml` 启用 schedule（每周）

---

### F-004 | High | master 分支保护未开启

**位置：** GitHub 仓库设置

**证据：**
```bash
gh api repos/{owner}/{repo}/branches/master --jq '.protection'
# → {"enabled":false, ...}
```

**影响：**
- CI 失败不阻断合入，ArchUnit/lifecycle-audit 全是建议非门禁
- AI 驱动开发失去「最终防线」，错误代码可直接进 master

**修复动作（人类决策）：**
1. 二选一：仓库转公开（解决分钟数计量） / 升级 GitHub Pro
2. 开启 master 分支保护，设 `build-and-guard`、`docs-validate`、`lifecycle-audit` 为 required checks
3. 合入前补 DEC 记录选型理由

**验收标准：**
- [ ] `gh api ...branches/master --jq '.protection.enabled'` 返回 `true`
- [ ] 三职位在 GitHub Settings → Branches → master → Required status checks 中勾选

---

### F-005 | High | AppContainer 暴露 infra 具体类型

**位置：** `app/src/main/kotlin/com/xcwk/scalehub/App.kt`

**证据：**
```kotlin
val profileStore: ProfileStore by lazy { ... }              // :73
val deviceRepository: RoomDeviceRepository by lazy { ... }  // :162
val bleLink: AndroidBleLink by lazy { ... }                 // :203
```

**影响：**
- ViewModel Factory 可直接调用 infra 非端口方法（如 `profileStore.clearMember(id)` 在 `App.kt:150` 已经这么干了）
- ArchUnit 桥面白名单放行，守卫不判红——语义泄漏合法化

**修复方案（推荐 A）：**
- `AppContainer` 对外属性一律声明为**端口类型**：
  - `profileStore` → 拆 `memberProfileReader: MemberProfileReader` + `memberProfileWriter: MemberProfileWriter`
  - `deviceRepository` → `deviceRepository: DeviceRepository`
  - `bleLink` → `bleLink: BlePort`
- 具体类型只在容器内部装配/持有

**验收标准：**
- [ ] `grep -n "ProfileStore\|RoomDeviceRepository\|AndroidBleLink" app/src/main/kotlin/com/xcwk/scalehub/App.kt` 仅出现在 `private`/`internal` 装配处，`val` 声明全为端口类型
- [ ] `ArchUnit` 新增负例：表现层经 `AppContainer` 方法调用返回类型不得位于 `infrastructure..`

---

### F-006 | High | 文档滞后于代码

**位置：** `README.md`、`docs/architecture/index.md`、`STATUS.md`、`docs/ai/lifecycle/`

**证据：**
- `STATUS.md` 最后更新 `2026-10-17T12:41:28Z`，DEC 计数 32，实际已有 DEC-034
- `README.md`「功能现状（M1）」仍只列手动体重，未提 BLE/云/AI/12 feature/NavState
- `docs/architecture/index.md` §1「云端同步（M2）是已规划但尚未接入的出站端口」——**已接入**

**修复路径（走治理流，不得直接改黄金标准）：**
1. 人类授权后新建 DEC（`gov-next-decision.sh --create`）
2. 重写 `docs/ai/lifecycle/01_PRD.md`（功能现状）、`02_Architecture.md`（补 BLE/Cloud/AI 端口、DataChange、NavState、组合根桥面）、`03_TaskGraph.md`
3. `bash scripts/workflow/wf-promote.sh <target> --decision <DEC>` 晋升
4. `bash scripts/governance/gov-update-status.sh` 重新生成 STATUS.md
5. README 功能现状与路线图同步

**验收标准：**
- [ ] 架构文档模块职责表与 `settings.gradle.kts`、源码目录一致
- [ ] `STATUS.md` DEC 计数与 `docs/ai/decisions/` 实际文件数一致
- [ ] `bash scripts/governance/gov-validate.sh` 0 FAIL

---

### F-007 | Medium | 仓储接口仍含查询方法（`between`/`recent`）

**位置：** `domain/src/main/kotlin/com/xcwk/scalehub/domain/repository/MeasurementRepository.kt`、`HealthLogRepository.kt`

**证据：** 接口仍含 `between(fromInc, toExc)`、`recent(limit)`

**影响：** 仓储被迫实现复杂查询，替换存储引擎（如 ObjectBox）成本高；`InMemoryMeasurementRepository` 为测试实现完整查询逻辑，维护成本高

**修复方向：**
- 仓储接口精简为 `save/save(memberId)/findById/remove`
- 查询迁移到 `application.query`（已有 `MeasurementQuery`/`HealthLogQuery` 骨架）

**验收标准：**
- [ ] `domain.repository` 接口仅含存取方法
- [ ] `application.query` 覆盖所有读取需求，单测全绿

---

### F-008 | Medium | 测试门禁缺口

**位置：** `.github/workflows/android-test.yml`（仅 `workflow_dispatch`，schedule 被注释）

**修复路线（沿用 DEC-029 组 B「先手动后门禁」）：**
1. 手动触发一次 `android-test` 全绿
2. 结果记入相关 DEC 的 Assumptions
3. 启用每周 schedule
4. UI 截图测试（Roborazzi 等）属新增依赖，单独 DEC 评估 CI 分钟成本（DEC-033）

**验收标准：**
- [ ] `android-test` 有成功记录
- [ ] schedule 启用
- [ ] 截图测试是否引入有 DEC 结论

---

### F-009 | Medium | 发布配置未就绪

**位置：** `BackendConfig.kt`（无 buildType 区分）、`app/build.gradle.kts`（release 未开 R8）

**修复路线（发布前起新 DEC）：**
1. `BackendConfig.BASE` 改由 `buildConfigField` 按 buildType 注入
2. release 开启 `isMinifyEnabled`，补 keep 规则（Room、org.json 反射点）
3. 评估会话 token 改用加密存储

---

## 三、执行顺序约束（必须按序，不可并行）

```
F-004 (分支保护)        ← 人类决策，0.5 小时
    ↓
F-001 (水位线/对账)     ← M2 核心，2~3 天
    ↓
F-002 (多成员隔离测试)  ← 依赖缺口 1，0.5 天
    ↓
F-003 (全链路测试)      ← 依赖缺口 1/2，1~1.5 天
    ↓
F-005 (AppContainer 端口化) ← 可与 1~3 并行，0.5 天
    ↓
F-006 (文档对齐)        ← 必须在 1~5 合入后，避免把中间态写进文档，1 天
    ↓
F-007/008/009               ← M2 中期/发布前并行
```

---

## 四、M2 最小可行启动条件（Definition of Ready）

- [ ] F-001：SyncService 有水位线 + 幂等 + ≥4 新测试
- [ ] F-002：多成员隔离 ≥3 测试
- [ ] F-003：JVM 集成测试 ≥5 场景 + androidTest 冒烟手动全绿
- [ ] F-004：master 分支保护开启，三职位 required
- [ ] F-005：AppContainer 全端口类型暴露 + ArchUnit 负例
- [ ] F-006：文档与 STATUS 对齐，gov-validate 0 FAIL

**六条全绿，才能开始写 M2 业务同步逻辑（多端合并、冲突 UI、增量策略调优）。**

---

## 五、给下一个 session 的接力信息

**本次核验的元教训：**

> 评审/分析的基线必须是**实时核验的代码**，不是 session 早期读到的快照，也不是他人评审里描述的工作区状态。DEC-034 从「未提交」到「已合入 master」只隔了几十分钟，期间我读到的所有文件内容全部过期。

**三条必跑命令：**

```bash
git status --short && git log --oneline -5                    # 工作区与提交
cat application/.../DataRefreshBus.kt | head -30              # 关键文件当前内容
head -30 docs/ai/decisions/DEC-034.md                         # 决策是否已落地
```

---

## 附：本次核验命令清单（可复现）

```bash
# 1. 架构债闭环确认
grep -n "SyncTrigger" application/.../service/*.kt         # 应为空
grep -n "OkokBia" infrastructure/                          # 应为空
grep -n "memberId" domain/.../MeasurementRepository.kt     # 应在签名上
find domain -name "Clock.kt"                               # 应存在
grep -n "create" domain/.../MeasureRecord.java             # 应有静态工厂
find application -name "SyncOnLocalWrites.kt"              # 应存在

# 2. 剩余缺口确认（F-001~F-009）
grep -n "watermark\|SyncState" application/.../SyncService.kt   # 应为空（缺口 1）
find app -path "*androidTest*" -name "*.kt"                     # 应为空（缺口 3）
gh api ...branches/master --jq '.protection.enabled'            # 应为 true（缺口 4）
grep -n "ProfileStore\|RoomDeviceRepository" app/.../App.kt     # 仅 private/装配处（缺口 5）
grep -n "between\|recent" domain/.../MeasurementRepository.kt   # 应为空（缺口 7）
```

---