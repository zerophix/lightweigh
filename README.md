# 轻衡健康 · LightWeigh

> 面向 OKOK 协议蓝牙体脂秤的个人健康管理 App。测量数据本地优先、端侧解析，支持加密云同步与多成员家庭档案。

[![版本](https://img.shields.io/badge/version-0.1.0-blue)](VERSION)
[![语言](https://img.shields.io/badge/language-Kotlin-orange)](#技术栈)
[![构建](https://img.shields.io/badge/build-Gradle-green)](#快速开始)
[![最低 SDK](https://img.shields.io/badge/minSdk-26-informational)](#技术栈)
[![许可](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

## 为什么是 LightWeigh

体脂秤 App 的难点不在界面，而在**数据可信**：协议多样、算法易漂移、离线与上云要一致、多成员不能串号。LightWeigh 把这些复杂度收敛在端侧与分层架构里：

- **协议与算法端侧自治** —— 三套体脂秤协议、两套体成分算法在 `domain` 层纯 JVM 实现，零 Android 依赖，黄金向量测试锁定输入输出，任何一端漂移即测试失败。
- **离线优先 + 加密云同步** —— 无网也能完整记录；联网后按水位线增量同步，幂等可对账，本地与云端双承诺互不牵制。
- **多成员显式隔离** —— `memberId` 贯穿数据、同步、报告与 AI 上下文，家庭成员健康数据彼此独立。
- **分层由编译器与守卫强制** —— 四模块依赖只向内，ArchUnit 架构守卫在构建期拦截越界。

## 核心能力

- 蓝牙体脂秤测量：体重 + 体成分（体脂率、BMI、肌肉量、水分率、内脏脂肪、基础代谢、骨量、身体评分）
- 手动记录与历史管理：测量列表、单条详情、日期/成员筛选、对比、编辑、删除
- 趋势分析：单指标/多指标曲线、时间范围筛选、数据点与异常值标注
- 家庭多成员档案：成员独立健康数据、目标管理、成员切换
- 云同步：测量记录 / 健康日志 / 档案增量同步，水位线 + 对账 + 幂等
- AI 健康助手（规划）：报告解读、趋势总结、每周行动计划，数据授权可控、可撤回

## 技术栈

Kotlin · Jetpack Compose · Room · Coroutines · ArchUnit（架构守卫）· Gradle（Kotlin DSL）

- `minSdk` 26 / `compileSdk` 34 · JDK 17+（推荐 Android Studio 自带 JBR 21）
- `applicationId`：`com.xcwk.scalehub`（与仓库名独立，改仓库名不影响已安装应用）

## 技术架构

四模块分层，依赖只允许向内（`domain` 零 Android 依赖，编译期强制）：

| 模块 | 类型 | 职责 |
|---|---|---|
| `domain` | 纯 JVM | 协议解析、体成分算法、测量模型、仓储端口 |
| `application` | 纯 JVM | 用例编排（取档案 → 算体脂 → 落库） |
| `infrastructure` | Android 库 | Room 存储、档案存储、（BLE 网关、云同步） |
| `app` | Android 应用 | Compose 界面（UDF 单向数据流）、组合根、架构守卫 |

## 快速开始

```bash
./gradlew build        # 构建 + 全部测试 + 架构守卫
```

**环境要求**：JDK 17+ · Android SDK 34

**Android Studio**：`Open` 选择本目录，Gradle JDK 选 embedded `jbr-21`。

**模拟器**：arm64 AVD（如 `qk_health_avd`）；蓝牙称重需真机。

## 项目结构

```
lightweigh/
├── domain/           # 纯 JVM：协议解析、体成分算法、模型、仓储端口
├── application/      # 纯 JVM：用例编排
├── infrastructure/   # Android 库：Room、档案、（BLE / 云同步）
├── app/              # Android 应用：Compose 界面、组合根、架构守卫
├── docs/             # 文档（架构 / 指南 / 决策 / 参考 / 原型）
├── design/           # 设计资产（154 屏静态原型 + 154 屏可交互原型）
├── tests/            # 治理与集成测试
└── scripts/          # 治理脚本、工作流、会话工具
```

## 协议与算法

体脂秤接入是本项目的核心资产：

- **协议**：`okok-1`（GATT 连接式）、`okok-broadcast-1`（纯广播）、`chipsea-cloud-1`（芯海云端版）。帧解析、分包重组、单位换算均在 `domain` 层，纯 JVM 可测。
- **算法**：`builtin-1`（经验公式）、`okok-bia-1`（设备回归公式）。编号定格历史记录，升级不回算。
- **正确性**：黄金向量测试守护，输入输出向量与跨端契约同源，任何一端漂移即测试失败。

## 设计资产

- **154 屏静态视觉原型**：`design/prototypes/`（设计基准，覆盖登录/测量/设备/趋势/AI/设置等全流程）
- **154 屏可交互原型**：`design/prototypes/interactive/`（模拟真实软件：一屏、点按钮真实跳转、弹窗/Toast 真实触发）
- **Figma 源**：`design/figma/`（60 屏官方设计稿，本地参考，不入库）

## 开发与协作

遵循项目协作协议（见 [AGENTS.md](AGENTS.md)）：

```bash
bash scripts/session/ses-context-load.sh        # 加载上下文与任务板
bash scripts/governance/gov-validate.sh          # 全量治理校验
bash scripts/governance/gov-local-ci.sh          # 合并前本地 CI（build + validate + custom-checks）
```

多会话并行开发请使用隔离 worktree（`ses-worktree.sh`），避免共享工作树抢分支。

## 测试

```bash
./gradlew test              # 单元测试
./gradlew build             # 构建 + 全部测试 + ArchUnit 架构守卫
bash scripts/governance/gov-validate.sh          # 治理测试
```

## 路线图

- **M2**：手机号登录与云端同步（CloudBase，离线保存与上云为两个独立承诺）
- **M3**：蓝牙称重（三协议接入，连接状态机：显式状态枚举 + 串行写队列 + 断连退避重连）
- **M4**：完整页面（设备绑定 / 趋势图表 / 体测报告 / 健康日志 / 设置 / 档案编辑）
- **M5**：8 电极分支算法（分部位阻抗 Z14/Z23 合并与专用系数）

## 相关文档

- [架构说明](docs/architecture/index.md)
- [技术决策记录](docs/ai/decisions/index.md)
- [开发过程记录](docs/ai/lifecycle/index.md)
- [工程反思与交付状态](docs/guides/GC-engineering-reflections.md)
- [参考（术语与口径）](docs/reference/index.md)

项目当前状态见 [STATUS.md](STATUS.md)（自动生成）。

## 许可证

[MIT](LICENSE)
