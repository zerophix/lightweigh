# 测试

> 验证策略：JVM 单元测试 + 黄金向量回归 + ArchUnit 架构守卫 + 治理文档校验。
> 覆盖型文档：每次迭代重写。

---

## 1. 验证策略

工程测试分两层：代码层（Gradle/JUnit）验证业务与架构，治理层（Python/Bash）验证文档与脚本。
代码层不依赖 Android 运行时（纯 JVM 单测），因此可在无模拟器的 CI 环境全量执行。

| 层级 | 覆盖范围 | 工具 | 通过标准 |
|------|---------|------|---------|
| 领域算法 | 体成分公式与黄金向量 | JUnit + Truth | 全绿 |
| 用例与仓储 | RecordMeasurement / 仓储契约 | JUnit + coroutines-test | 全绿 |
| 架构守卫 | 四模块依赖方向 | ArchUnit | 全绿（含负例） |
| 治理校验 | docs 结构/命名/语言/记忆时效 | gov-validate.sh | 0 违规 |

## 2. 测试清单

| 测试文件 | 单元测试方法 | 覆盖内容 |
|---------|:-----------:|---------|
| OkokBiaGoldenTest.java | 2 | okok-bia 黄金向量通过、8 电极占位向量 |
| WeightValueTest.kt | 4 | 公斤直通 / 斤折公斤 / 磅常数 / 英石拆包 |
| MeasureRecordTest.kt | 7 | 聚合不变量：时间戳、体重量程、阻抗上下界、体脂范围、哨兵归一化 |
| RecordMeasurementServiceTest.kt | 5 | 算体脂落库、计算输入快照持久化、改档案不改快照、无阻抗 -1、算法回落 |
| InMemoryMeasurementRepositoryTest.kt | 2 | recent 倒序限数、同时间戳幂等覆盖 |
| ArchitectureTest.kt | 6 | 4 条依赖规则 + 2 条自证元测试（解析自检 / 违规负例） |

合计 26 个单元测试方法（`@Test`），分布覆盖 domain / application / infrastructure / app 四模块
（`RuleViolationFixture.kt` 是被守卫引用的夹具、不含 `@Test`，不计入）。按 Gradle 构建变体展开
（app 与 infrastructure 各含 debug + release）后的**测试执行数**另计。术语定义见
[术语与口径](../../reference/index.md)，数值快照见 [06_Metrics](06_Metrics.md)。

## 3. 运行方式

```
./gradlew build                            # 编译 + 全部 JVM 测试 + ArchUnit
bash scripts/governance/gov-validate.sh    # 治理文档校验
python3 -m pytest tests/ -q                # 治理测试用例（负向校验 + 漂移守卫）
```

架构守卫以两条元测试自证：`archunit能解析当前类文件`（防解析退化导致规则静默空转）与
`守卫负例_违规的feature依赖会被规则捕获`（用夹具证明违规必红）。

## 4. 未覆盖边界

- **BLE 真机行为**：M3 之前无 BluetoothGatt 集成测试，协议解析仅以单元测试覆盖。
- **云同步与登录**：M2 之前 SyncGateway 未实现，无集成测试。
- **Room 迁移**：当前 version=1 无迁移；升版本时必须补 MigrationTest（schema 快照已入库）。
- **UI 交互**：Compose 无 UI 测试，首屏逻辑靠 ViewModel 单测间接覆盖。

## 5. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v3 | 2026-10-16 | 清单统一术语口径（单元测试方法 26）+ 补聚合不变量 / 单位向量用例（DEC-019） |
| v2 | 2026-10-16 | 改为真实工程测试策略：JVM 单测 + 黄金向量 + ArchUnit + 治理校验 |
| v1 | 2026-07-10 | 初始验证计划：模板项目的文档校验体系 |
