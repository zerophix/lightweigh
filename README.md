# 轻衡健康 Android（qk-health-v2）

> 配合 OKOK 协议蓝牙体脂秤使用的个人健康记录 App。本地优先存储，支持云端同步与多成员档案。

![版本](https://img.shields.io/badge/version-0.1.0-blue)

![语言](https://img.shields.io/badge/language-Kotlin-orange)

![构建](https://img.shields.io/badge/build-Gradle-green)

![许可](https://img.shields.io/badge/license-MIT-lightgrey)

## 简介

轻衡健康帮助你记录体重与体成分数据。连接蓝牙体脂秤后，测量结果自动落库，  
并可选加密同步到云端。支持多个家庭成员，各自拥有独立的健康档案与测量记录。

- **本地优先**：无网络也能完整记录，联网后按水位线增量同步
- **多成员**：`memberId` 显式隔离，首页 / 记录 / 趋势 / 报告 / AI 上下文随成员切换
- **协议解析在端侧**：体脂秤三套协议与体成分算法在 `domain` 层纯 JVM 实现，黄金向量守护

## 功能特性

- 手动记录体重（体脂按 `builtin-1` 公式计算，无阻抗时记为「未计算」）
- 最近测量列表 / 单条详情 / 历史筛选
- 成员档案字段：性别 / 出生年 / 身高（体脂计算输入）
- 健康日志记录（饮食 / 饮水 / 运动）
- **云同步（M2 已实现）**：测量记录 / 健康日志 / 档案增量推送，水位线 / 对账 / 幂等
- **多成员支持**：`memberId` 显式隔离，水位线按 `(tableName, memberId)` 复合主键
- **蓝牙称重（已落地）**：三协议（OKOK GATT / OKOK 广播 / Chipsea 云端）；真机秤已连通；DEC-044 修复 Chipsea 混合广播误识别

## 技术栈

Kotlin · Jetpack Compose · Room · Coroutines · ArchUnit（架构守卫）

## 技术架构

四模块分层，依赖只允许向内：

| 模块               | 类型                        | 职责                                       |
| ---------------- | ------------------------- | ---------------------------------------- |
| `domain`         | 纯 JVM（零 Android 依赖，编译期强制） | 协议解析、体成分算法、测量模型、仓储端口                     |
| `application`    | 纯 JVM                     | 用例编排（`RecordMeasurement`：取档案 → 算体脂 → 落库） |
| `infrastructure` | Android 库                 | Room（v7）、BLE 网关、云同步、会话、习惯提醒                 |
| `app`            | Android 应用                | Compose 界面（UDF 单向数据流）、组合根、ArchUnit 架构守卫  |

## 快速开始

```bash
./gradlew build        # 构建 + 全部测试 + 架构守卫
```

**环境要求**

- JDK 17+（推荐 Android Studio 自带 JBR 21）
- Android SDK 34

**Android Studio**：`Open` 选择本目录，Gradle JDK 选 embedded `jbr-21`。

**模拟器**：使用 arm64 AVD（如 `qk_health_avd`）；蓝牙称重需真机。

`applicationId`：`com.xcwk.scalehub`

## 项目结构

```
qk-health-v2/
├── domain/           # 纯 JVM：协议解析、体成分算法、模型、仓储端口
├── application/      # 纯 JVM：用例编排
├── infrastructure/   # Android 库：Room、BLE、云同步、档案、习惯
├── app/              # Android 应用：Compose 界面、组合根、架构守卫
├── docs/             # 文档（架构 / 指南 / 决策 / 参考）
├── tests/            # 治理与集成测试
└── scripts/          # 治理脚本、工作流、会话工具
```

## 开发

每次迭代遵循项目的协作协议（见 [AGENTS.md](AGENTS.md)）：

```bash
bash scripts/session/ses-context-load.sh        # 加载上下文与任务板
bash scripts/governance/gov-validate.sh          # 全量治理校验
bash scripts/governance/gov-update-status.sh     # 刷新状态看板
```

## 测试

```bash
./gradlew test              # 单元测试
./gradlew build             # 构建 + 全部测试 + ArchUnit 架构守卫
bash scripts/governance/gov-validate.sh          # 治理测试
```

架构约束由 ArchUnit 在编译期/测试期强制（`domain` 零 Android 依赖、  
分层依赖只向内、组合根仅装配等）。

## 协议与算法

体脂秤接入是本项目的核心资产：

- **协议**：`okok-1`（GATT 连接式）、`okok-broadcast-1`（纯广播）、`chipsea-cloud-1`（芯海云端版）。  
  帧解析、分包重组、单位换算均在 `domain` 层，纯 JVM 可测。
- **算法**：`builtin-1`（经验公式）、`okok-bia-1`（设备回归公式）。编号定格历史记录，升级不回算。
- **正确性**：由黄金向量测试守护（输入输出向量与跨端契约同源，任何一端漂移即测试失败）。

## 路线图

- **已完成**：云同步与账号（M2）、蓝牙称重三协议与真机连通（M3）、多成员 / 趋势 / 报告 / 习惯 / 设置等主路径页面（M4 主体）
- **进行中**：以 `.ai/tasks.json` 为准（当前 pending 含新手引导、报告分析 Tab、AI 结构化卡片等，见 T-045+）
- **后续**：8 电极分支算法等（分部位阻抗专用系数）；开源与分支保护等工程卫生项见近期评审

## 相关文档

- [架构说明](docs/architecture/index.md)
- [技术决策记录](docs/ai/decisions/index.md)
- [开发过程记录](docs/ai/lifecycle/index.md)
- [工程反思与交付状态](docs/guides/GC-engineering-reflections.md)
- [参考（术语与口径）](docs/reference/index.md)

项目当前状态见 [STATUS.md](STATUS.md)（自动生成）。

## 许可证

[MIT](LICENSE)
