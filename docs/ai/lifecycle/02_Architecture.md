# 架构设计

> lightweigh（轻衡健康 Android 客户端）采用 DDD 分层四模块工程，包坐标 com.xcwk.scalehub；
> 依赖方向由编译器与 ArchUnit 双重强制。
> 覆盖型文档：每次迭代重写，保留核心决策但不保留旧版本。

---

## 1. 系统上下文

轻刻健康是一款配合蓝牙体脂秤使用的单进程 Android 应用。当前已落地的外部依赖：

- **本机 Room/SQLite**（`AppDatabase` version **7**，schema 快照 1~7 入库）
- **蓝牙称重**（`BlePort` ← `AndroidBleLink`；协议 `okok-1` / `okok-broadcast-1` / `chipsea-cloud-1`；用户确认真机秤可用；DEC-044 修复 Chipsea 混合广播版本误识别）
- **云端同步与账号**（CloudBase 网关、水位线/对账/幂等；手机号/邮箱/微信登录入口已接线）
- **习惯提醒**（Room 习惯表 + WorkManager 调度）

App 不对外提供被调用接口，是纯粹的调用方。组合根 `AppContainer`（位于 `App.kt`）对外只暴露端口与用例，不泄漏 infrastructure 具体类型（DEC-035）。

```
┌───────────────┐   BLE 称重(已落地)  ┌────────────────────┐
│  蓝牙体脂秤    │◄──────────────────►│                    │
└───────────────┘                    │                    │
┌───────────────┐   云同步(已落地)    │    轻刻健康 App     │
│  云端账号体系  │◄──────────────────►│  com.xcwk.scalehub │
└───────────────┘                    │                    │
┌───────────────┐   Room v7           │                    │
│  本地 SQLite   │◄──────────────────►│                    │
└───────────────┘                    └────────────────────┘
```

## 2. 分层架构

```
        ┌──────────────────────────────────────────────┐
        │  :app  (Android application)                  │
        │  ├── App / AppContainer   组合根(唯一装配点)   │
        │  ├── feature/measure       Compose + ViewModel 四件套│
        │  └── feature/* + blePort   称重/设备/习惯/登录等     │
        └───────────────────┬──────────────────────────┘
                            │ 依赖(向下)
        ┌───────────────────▼────────────┐   ┌────────────────────────────┐
        │  :application (纯 JVM)          │   │  :infrastructure (Android)  │
        │  RecordMeasurementService       │◄──┤  RoomMeasurementRepository  │
        │  MeasurementQuery               │实现│  InMemoryMeasurementRepo    │
        │  command / query                │端口│  ProfileStore               │
        └───────────────────┬────────────┘   └──────────────┬─────────────┘
                            │ 依赖(向下)                     │ 实现端口
        ┌───────────────────▼────────────────────────────────▼─────────────┐
        │  :domain (纯 JVM)                                                  │
        │  model: MeasureRecord / Member / HealthLog / ScaleDevice           │
        │  protocol: okok / chipsea / packet 解析器                          │
        │  algorithm: BuiltinComposition(builtin-1) / OkokBia(okok-bia-1)    │
        │  repository(端口): MeasurementRepository / MemberProfileReader     │
        │  types: DomainException                                            │
        └────────────────────────────────────────────────────────────────────┘
```

## 3. 模块职责

| 模块 | 类型 | 职责 | 关键内容 |
|------|------|------|---------|
| :domain | 纯 JVM | 领域内核：模型、协议解析、体成分算法、端口 | 28 个源文件（`src/main` 下 `*.kt` + `*.java`）：model 4 / protocol 17 / algorithm 4 / repository 2 / types 1（含 `Clock`） |
| :application | 纯 JVM | 用例编排（写 command + 读 query）+ 同步编排 | RecordMeasurementService、MeasurementQuery、SyncService（294 行，水位线/对账/幂等）、SyncTrigger、SyncOnLocalWrites、DataRefreshBus（`DataChange` sealed 事件） |
| :infrastructure | Android 库 | 端口实现层（Room / SharedPreferences / CloudBase） | AppDatabase（v6，含 `sync_state` 表）、MeasurementDao/Po、两个仓储实现、ProfileStore、CloudBaseGateway（93 行，GET/POST/DELETE + 分块 40 + Prefer: merge-duplicates）、RoomSyncStateRepository |
| :app | Android 应用 | 组合根 + feature UI 层 + 架构守卫测试 | App/AppContainer（全端口类型暴露）、feature/measure 四件套、ArchitectureTest（14 条规则，含 `appcontainer公开属性不得暴露infra具体类型`） |

`:domain` 是内核，不依赖任何其他模块；`:infrastructure` 实现 `:domain` 定义的端口，构成依赖倒置。

## 4. 依赖方向与守护

依赖方向为 `feature → application → domain ← infrastructure`（trigger 为 BLE 回调/后台任务
预留落点，与 feature 同属被守卫的内层禁区），由两条独立机制强制：

### 编译器守护

`:domain` 与 `:application` 是 `kotlin-jvm` 纯 JVM 模块，不引入 AGP，因而没有 `android.jar`。
领域层一旦引用 `android.*` / `androidx.*` 会直接编译失败——这比运行期检查更硬。

### ArchUnit 守卫

`app/src/test/.../ArchitectureTest.kt` 以 14 条依赖规则守住跨模块方向、"UI 不绕过用例"
与存储 SDK 隔离（T-006 上锁，DEC-021 task A；T-014 补 `appcontainer公开属性不得暴露infra具体类型` 负例）：

```
:domain ◄── :application ◄── :app
   ▲                            │
   └──────── :infrastructure ◄──┘  (实现端口，不得依赖 feature/trigger)
```

守卫内含两条自证元测试：`archunit能解析当前类文件`（防 ASM 解析退化导致规则静默空转）与
两组守卫负例（feature 依赖违规、存储 SDK 依赖违规，均用夹具证明必红）；连同 6 条依赖规则，
`ArchitectureTest.kt` 合计 9 个单元测试方法。

## 5. 运行时数据流

```
写:  MeasureScreen(intent) → MeasureViewModel.onIntent
        → RecordMeasurementService.execute(RecordMeasurementCommand)
            → Algorithms.of(id) 回落到 builtin-1
            → algorithm.fatPercent(...)       // 无阻抗则 fat = -1 哨兵
            → MeasurementRepository.save → MeasurementDao.upsert
读:  MeasureViewModel.refresh → MeasurementQuery.recent
        → RoomMeasurementRepository.recent → MeasurementDao → Po.toDomain()
```

UI 采用 UDF 三件套（`MeasureUiState` / `MeasureIntent` / `MeasureViewModel`），`MainActivity`
只负责把首屏交给 Compose。时间戳在用例层盖章，存储层不各自盖章。

## 6. UI 架构（M4 立约，DEC-021）

旧库 qk-health-android 的 UI 因「页面直连存储与云 + UiRefresh 全局刷新总线 + 千行页面文件」
被判定不可持续扩展；v2 立约以下五条，先于 M4 任何页面代码生效：

### 6.1 分层铁律：UI 零业务

```
Compose Screen ──渲染── UiState(不可变)   dispatch(Event) ──► ViewModel
                                                              │ 只调用
                                                              ▼
                                        application 服务/查询 (suspend / Flow)
```

趋势筛选、月度汇总、差值计算、AI 摘要的业务上下文全部在 :application；Composable 只渲染，
ViewModel 只编排。由 ArchUnit 守卫机械强制（app 的 ui 包禁 import infrastructure、存储与云
SDK），规则随 M4 首个垂直切片落地（DEC-021 task A）——先立约、后上锁，锁的落地时点如实标注。

### 6.2 UDF 替代全局刷新总线

每屏一个 `StateFlow<UiState>`，动作为 sealed Event，一次性效果（导航/Toast/权限）走独立
Effect 通道。跨屏刷新由**数据源层信号的订阅关系**决定（落库信号 → 订阅方自然重算），
禁止复刻旧库 `UiRefresh.version++` 全局计数——谁该刷新必须可推理，不靠页面自觉。

### 6.3 屏幕四件套（新增页面 = 新增目录 + 路由表一行）

```
feature/<name>/
├── <Name>Screen.kt      # 无状态 Composable，@Preview 覆盖全状态组合
├── <Name>ViewModel.kt    # 调 application、映射 UiModel、处理 Event
├── <Name>UiState.kt      # State / sealed Event / sealed Effect
└── <Name>Route.kt        # 类型安全路由
```

### 6.4 读模型下沉

application 查询服务按 UiState 形状直出读模型（沿用 MeasurementQuery 方向：日末筛选、
范围聚合在服务层完成并黄金测试），ViewModel 只做转发与格式化——UI 拿到即渲染，
翻转旧库「UI 取原始记录自己筛」的模式。

### 6.5 设计系统与导航

色彩/字号/间距 token 与 5-8 个基础组件先立（卡片/空态/加载/面板/表单行），
页面禁止散落硬编码样式。导航为类型安全 Route，**需求文档的信息架构树即导航图规格**；
「报告 → 去完善 → 返回仍在报告」类返回语义单独立导航测试。

### 6.6 交付节奏

M4 按垂直切片推进：一次一个页面四件套，过「预览 + ViewModel JVM 测试 + 架构守卫」三关
再进下一个；禁止旧库式 big-bang（14 个千行文件后集成）。

## 7. 核心架构决策

| 决策 | 方案（采纳） | 替代方案 | 理由 |
|------|------------|---------|------|
| 分层守护 | 编译器 + ArchUnit 双保险 | 仅 ArchUnit | 纯 JVM 模块让 Android 依赖编译即失败 |
| 依赖倒置 | 端口定义在 domain，实现在 infrastructure | 实现接口置于外层 | 内核不依赖实现，便于替换与测试 |
| 存储 | Room 关系表 | 旧库 SharedPreferences JSON | 关系表无 180 条截断，可按契约查询 |
| 装配 | 手写 AppContainer | Hilt | 当前规模简单，装配点唯一，可平移 |
| 领域错误 | domain.types.DomainException | 外层吞异常返回魔法值 | 错误类型归领域，文案转换归外层 |
| UI 状态管理 | UDF + 每屏不可变 UiState（DEC-021） | 旧库 UiRefresh 全局计数总线 | 刷新由订阅关系决定，可推理可测试 |
| 页面组织 | 屏幕四件套按 feature 目录 | 千行 Pages.kt 页面即文件 | 新增页面零改动现有页面，冲突面最小 |
| UI 读路径 | application 直出读模型 | UI 取原始记录自组 | 筛选规则可黄金测试，UI 薄至可预览覆盖 |

## 8. 约束条件

- `:domain` 与 `:application` 禁止任何 Android 依赖（编译期强制）
- 领域内禁止抛 `android.*` 异常，禁止以 -1 充当错误码（-1 仅作"未计算"哨兵）
- 改 Room 实体必须 version+1、写 Migration、导出 schema 快照入库
- 统一包坐标 com.xcwk.scalehub（DEC-011）
- UI 层禁止业务规则与全局刷新总线；跨屏刷新只经数据源信号订阅（DEC-021，
  ArchUnit 机械守卫随 M4 首切片落地）

## 9. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v4 | 2026-10-22 | 系统上下文改为已落地事实：BLE/云/习惯/Room v7；组合根端口化（DEC-035/044/045） |
| v3 | 2026-10-16 | 新增 §6 UI 架构立约（DEC-021）：UDF/四件套/读模型/设计系统/垂直切片 |
| v2 | 2026-10-16 | 重写为轻刻健康真实分层架构（四模块 DDD） |
| v1 | 2026-07-10 | 初始架构：模板协作协议框架定义 |
