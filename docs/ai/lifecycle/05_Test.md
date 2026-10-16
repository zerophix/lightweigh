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

| 测试文件 | 用例数 | 覆盖内容 |
|---------|-------|---------|
| OkokBiaGoldenTest.java | 2 | okok-bia 黄金向量、builtin-1 回落 |
| RecordMeasurementServiceTest.kt | 3 | fat=-1 哨兵、age 守卫、算法回落 |
| InMemoryMeasurementRepositoryTest.kt | 2 | 最新在前、limit 截断 |
| ArchitectureTest.kt | 6 | 依赖方向 + 解析自检 + 违规负例 |

合计 13 个用例，分布覆盖 domain / application / infrastructure / app 四模块。

## 3. 运行方式

```
./gradlew build                            # 编译 + 全部 JVM 测试 + ArchUnit
bash scripts/governance/gov-validate.sh    # 治理文档校验
python3 -m pytest tests/ -q                # 治理负向测试
```

架构守卫以两条元测试自证：`archunit能解析当前类文件`（防解析退化导致规则静默空转）与
`守卫负例_违规的trigger依赖会被规则捕获`（用夹具证明违规必红）。

## 4. 未覆盖边界

- **BLE 真机行为**：M3 之前无 BluetoothGatt 集成测试，协议解析仅以单元测试覆盖。
- **云同步与登录**：M2 之前 SyncGateway 未实现，无集成测试。
- **Room 迁移**：当前 version=1 无迁移；升版本时必须补 MigrationTest（schema 快照已入库）。
- **UI 交互**：Compose 无 UI 测试，首屏逻辑靠 ViewModel 单测间接覆盖。

## 5. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v2 | 2026-10-16 | 改为真实工程测试策略：JVM 单测 + 黄金向量 + ArchUnit + 治理校验 |
| v1 | 2026-07-10 | 初始验证计划：模板项目的文档校验体系 |
