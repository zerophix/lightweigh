# 技术参考

> 轻衡健康 Android 客户端的协议、算法与存储契约参考。
> 不可直接编辑：由 DEC 或生命周期文档晋升后写入。

---

## 1. 领域协议

体脂秤接入是本工程的核心资产。三种报文各自对应 `domain/protocol` 下的解析器，
按编号定格历史兼容性（升级不回算）：

| 协议编号 | 载体 | 解析位置 | 说明 |
|---------|------|---------|------|
| okok-1 | GATT 连接式 | `domain/protocol/okok` | 连接后按特征收发帧 |
| okok-broadcast-1 | 纯广播 | `domain/protocol/okok` | 无需连接，扫描即收 |
| chipsea-cloud-1 | 芯海云端版 | `domain/protocol/chipsea` | 云端协议适配 |

分包重组、单位换算与十六进制工具（`domain/protocol/packet`、`HexUtil`）都在领域层，
纯 JVM 可测，不依赖 Android 运行时。

## 2. 体成分算法

| 算法编号 | 类型 | 输入 | 说明 |
|---------|------|------|------|
| builtin-1 | 经验公式 | 性别 / 年龄 / 身高 / 体重 | 无阻抗时的默认算法 |
| okok-bia-1 | 设备回归公式 | 上述 + 阻抗 | 有阻抗时使用 |

- 无阻抗时体脂率记为 `-1`（"未计算"哨兵，**不是**错误码）
- 算法正确性由黄金向量测试守护：输入输出向量与跨端契约（相邻 spec 仓库）同源，
  任何一端漂移即测试失败

## 3. 存储与契约

| 位置 | 实现 | 契约 |
|------|------|------|
| measurements 表 | Room（`infrastructure/persistent`） | 字段按 data-schema 契约，取代旧库 SharedPreferences JSON（无 180 条截断） |
| 成员档案 | ProfileStore | 性别 / 出生年 / 身高（体脂计算输入） |

改 Room 实体必须 version+1、写 Migration、导出 schema 快照入库。

## 4. 模块与依赖

四模块分层与依赖方向见 [架构设计](../architecture/index.md)。纯 JVM 模块（`:domain`、
`:application`）不引入 AGP，Android 依赖编译期失败；跨模块方向由 ArchUnit 守卫。

## 5. 治理工具

工程由 ai-project-template 治理层承载：`gov-validate.sh` 全量校验（规则族见 AGENTS.md
速查表）、`gov-next-decision.sh` 分配决策编号、`wf-promote.sh` 晋升黄金标准。

## 6. 术语与口径

本表是全仓库数量口径的**唯一事实源**。任何文档引用下述数量时必须使用本表的术语原名；
「N 项测试」「N 个用例」这类不写口径的表述一律禁止。写作规则见
[文档标准](../ai/guides/docstandard.md)；指标类数值以自动生成的
[06_Metrics](../ai/lifecycle/06_Metrics.md) 为准，其他文档不重复维护成表。

| 术语 | 口径（定义） | 事实源 / 算法 |
|------|------------|--------------|
| 模块数 | `settings.gradle.kts` 中 `include(...)` 的模块数 | settings.gradle.kts |
| 源文件 / 源码行数 | 各模块 `src/main` 下 `*.kt` + `*.java` 的文件数 / 物理行数 | 06_Metrics.md |
| 测试文件 / 测试代码行数 | 各模块 `src/test` 下 `*.kt` + `*.java` 的文件数 / 物理行数 | 06_Metrics.md |
| 单元测试方法 | 源码中 `@Test` 注解的方法数，与构建变体无关 | `grep -rho '@Test'` |
| 测试执行数 | Gradle 变体展开后实际执行的测试数 | `*/build/test-results`（app 与 infrastructure 各含 debug + release） |
| 治理测试用例 | `tests/` 下 pytest 收集的 test 函数数 | tests/python-test-registry.yaml |
| 协议实现 | `:domain` 中 `implements ScaleProtocol` 的类数 | 领域协议 |
| 体成分算法 | `:domain` 中 `implements BodyComposition` 的类数 | 体成分算法 |
| 黄金向量 | 体成分跨端一致性向量的条数 | body-composition-vectors.tsv |
| 文档版本 | 文档「版本历史」表的 vN，仅指该文档的修订号 | 各文档版本历史 |
| 产品版本 | Android `versionName`，即 STATUS.md 的「版本」栏 | STATUS.md |

未列出的计数术语，须在首次出现处给出定义；否则改用本表已有术语。

### 历史记录口径对照

决策日志、变更日志、收件箱与评审报告是追加型历史记录，正文不改写。其中出现的旧表述按
本表解释：

- DEC-011 中的裸「N 项测试」= 测试执行数（当时未标注口径），等价于单元测试方法的变体展开。
- DEC-002 / DEC-006 / DEC-008 / DEC-010 中的「N 个用例 / N 个测试」= 治理测试用例。

## 7. API 规格

- [REF-api-spec.md](REF-api-spec.md) — HTTP / WebSocket / gRPC 接口定义、请求响应结构、错误码、版本策略

## 8. Notion 索引

- [REF-notion-index.md](REF-notion-index.md) — 轻衡健康 Notion 空间 74 页页面与仓库路径对照

---

## 来源: ai/decisions/DEC-055
