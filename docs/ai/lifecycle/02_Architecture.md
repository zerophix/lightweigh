# 架构设计

> qk-health-v2（轻刻健康 Android 客户端）采用 DDD 分层四模块工程，包坐标 com.xcwk.scalehub；
> 依赖方向由编译器与 ArchUnit 双重强制。
> 覆盖型文档：每次迭代重写，保留核心决策但不保留旧版本。

---

## 1. 系统上下文

轻刻健康是一款配合蓝牙体脂秤使用的单进程 Android 应用。当前唯一已落地的外部依赖是本机
Room/SQLite 存储；蓝牙称重（M3）与云端同步（M2）是已规划但尚未接入的出站端口。App 不对外
提供被调用接口，是纯粹的调用方。

```
┌───────────────┐   BLE 称重(M3)   ┌────────────────────┐
│  蓝牙体脂秤    │◄────────────────►│                    │
└───────────────┘                  │                    │
┌───────────────┐   云同步(M2)     │    轻刻健康 App     │
│  云端账号体系  │◄────────────────►│  com.xcwk.scalehub │
└───────────────┘                  │                    │
┌───────────────┐   Room(已实现)   │                    │
│  本地 SQLite   │◄────────────────►│                    │
└───────────────┘                  └────────────────────┘
```

## 2. 分层架构

```
        ┌──────────────────────────────────────────────┐
        │  :app  (Android application)                  │
        │  ├── App / AppContainer   组合根(唯一装配点)   │
        │  └── trigger/ui            Compose + ViewModel │
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
| :domain | 纯 JVM | 领域内核：模型、协议解析、体成分算法、端口 | 28 个源文件（`src/main` 下 `*.kt` + `*.java`）：model 4 / protocol 17 / algorithm 4 / repository 2 / types 1 |
| :application | 纯 JVM | 用例编排（写 command + 读 query） | RecordMeasurementService、MeasurementQuery |
| :infrastructure | Android 库 | 端口实现层（Room / SharedPreferences） | AppDatabase、MeasurementDao/Po、两个仓储实现、ProfileStore |
| :app | Android 应用 | 组合根 + trigger 层 + 架构守卫测试 | App/AppContainer、Compose 首屏、ArchitectureTest |

`:domain` 是内核，不依赖任何其他模块；`:infrastructure` 实现 `:domain` 定义的端口，构成依赖倒置。

## 4. 依赖方向与守护

依赖方向为 `trigger → application → domain ← infrastructure`，由两条独立机制强制：

### 编译器守护

`:domain` 与 `:application` 是 `kotlin-jvm` 纯 JVM 模块，不引入 AGP，因而没有 `android.jar`。
领域层一旦引用 `android.*` / `androidx.*` 会直接编译失败——这比运行期检查更硬。

### ArchUnit 守卫

`app/src/test/.../ArchitectureTest.kt` 以 4 条依赖规则守住跨模块方向与"UI 不绕过用例"：

```
:domain ◄── :application ◄── :app
   ▲                            │
   └──────── :infrastructure ◄──┘  (实现端口，不得依赖 trigger)
```

守卫内含两条自证元测试：`archunit能解析当前类文件`（防 ASM 解析退化导致规则静默空转）与
`守卫负例_违规的trigger依赖会被规则捕获`（用夹具证明违规必红）；连同 4 条依赖规则，
`ArchitectureTest.kt` 合计 6 个单元测试方法。

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

## 6. 核心架构决策

| 决策 | 方案（采纳） | 替代方案 | 理由 |
|------|------------|---------|------|
| 分层守护 | 编译器 + ArchUnit 双保险 | 仅 ArchUnit | 纯 JVM 模块让 Android 依赖编译即失败 |
| 依赖倒置 | 端口定义在 domain，实现在 infrastructure | 实现接口置于外层 | 内核不依赖实现，便于替换与测试 |
| 存储 | Room 关系表 | 旧库 SharedPreferences JSON | 关系表无 180 条截断，可按契约查询 |
| 装配 | 手写 AppContainer | Hilt | 当前规模简单，装配点唯一，可平移 |
| 领域错误 | domain.types.DomainException | 外层吞异常返回魔法值 | 错误类型归领域，文案转换归外层 |

## 7. 约束条件

- `:domain` 与 `:application` 禁止任何 Android 依赖（编译期强制）
- 领域内禁止抛 `android.*` 异常，禁止以 -1 充当错误码（-1 仅作"未计算"哨兵）
- 改 Room 实体必须 version+1、写 Migration、导出 schema 快照入库
- 统一包坐标 com.xcwk.scalehub（DEC-011）

## 8. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v2 | 2026-10-16 | 重写为轻刻健康真实分层架构（四模块 DDD） |
| v1 | 2026-07-10 | 初始架构：模板协作协议框架定义 |
