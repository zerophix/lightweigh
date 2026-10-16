# 技术参考

> 轻刻健康 Android 客户端的协议、算法与存储契约参考。
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

## 来源: ai/decisions/DEC-014
