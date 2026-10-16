# 轻刻健康 Android（qk-health-v2）

配合 OKOK 协议蓝牙体脂秤使用的个人健康记录 App。记录体重与体成分数据，
本地优先存储，支持后续云端同步与多成员档案。

## 功能现状（M1）

- 手动记录体重（体脂按 builtin-1 公式计算，无阻抗时记为"未计算"）
- 最近测量列表
- 成员档案字段：性别 / 出生年 / 身高（体脂计算输入）

## 技术架构

Kotlin + Jetpack Compose + Room + Coroutines，四模块分层，依赖只允许向内：

| 模块 | 类型 | 职责 |
|---|---|---|
| domain | 纯 JVM（零 Android 依赖，编译期强制） | 协议解析、体成分算法、测量模型、仓储端口 |
| application | 纯 JVM | 用例编排（RecordMeasurement：取档案 → 算体脂 → 落库） |
| infrastructure | Android 库 | Room 存储、档案存储、（后续：BLE 网关、云同步） |
| app | Android 应用 | Compose 界面（UDF 单向数据流）、组合根、ArchUnit 架构守卫 |

## 快速开始

```bash
./gradlew build        # 构建 + 全部测试 + 架构守卫
```

- 环境：JDK 17+（推荐 Android Studio 自带 JBR 21）、Android SDK 34
- Android Studio：Open 选择本目录，Gradle JDK 选 embedded jbr-21
- 模拟器：使用 arm64 AVD（如 qk_health_avd）；蓝牙称重需真机
- applicationId：com.xcwk.scalehub

## 协议与算法

体脂秤接入是本项目的核心资产：

- 协议：okok-1（GATT 连接式）、okok-broadcast-1（纯广播）、chipsea-cloud-1（芯海云端版），
  帧解析、分包重组、单位换算均在 domain 层，纯 JVM 可测
- 算法：builtin-1（经验公式）、okok-bia-1（设备回归公式），编号定格历史记录，升级不回算
- 算法正确性由黄金向量测试守护（输入输出向量与跨端契约同源，任何一端漂移即测试失败）

## 路线图

- M2：手机号登录与云端同步（CloudBase，离线保存与上云为两个独立承诺）
- M3：蓝牙称重（三协议接入，连接状态机：显式状态枚举 + 串行写队列 + 断连退避重连）
- M4：完整页面（设备绑定 / 趋势图表 / 体测报告 / 健康日志 / 设置 / 档案编辑）
- M5：8 电极分支算法（分部位阻抗 Z14/Z23 合并与专用系数）

## 相关文档

- [docs/architecture/index.md](docs/architecture/index.md) — 架构说明
- [docs/ai/decisions/](docs/ai/decisions/index.md) — 技术决策记录
- [docs/ai/lifecycle/](docs/ai/lifecycle/index.md) — 开发过程记录

## 当前状态

M1 完成：四模块可构建，21 项测试全绿（算法黄金向量 / 用例 / 存储 / 架构守卫）。

### 反思

- 教训：算法必须与设备电极模式严格匹配——早期实现对 8 电极秤套用四电极公式，数值能产出但口径错配，这类静默偏差比崩溃更危险；现已按模式拆分系数并用黄金向量锁定。
- 坑：本地存储带隐式容量上限等于静默丢数据；v2 用 Room 取代旧存储后该问题从结构上消除，但容量与保留语义必须写进契约。
- 教训：批量包名/路径重命名不能依赖 shell 内建替换（转义行为不可控），必须用脚本语言做确定性操作，并在改后做残留复验。
- 教训：空模板事故的根因不是操作失误而是校验盲区——唯一会检查占位符的脚本从未被任何门禁调用，规则写了却不执行等于没写；补规则时又发现按治理笔记的「通用尖括号」思路会误伤合法正文，最终只有用模板 token 白名单并用测试钉住它与模板同步才可靠。
- 坑：去模板化收尾不能只改正文——状态看板的项目名取自 `mkdocs.yml` 的 `site_name`，指标由自动脚本从代码统计生成；不先改源头就重生成，模板身份会立刻「复活」。正确顺序是「先改源头 → 再重生成 → 最后校验」，否则每次收尾都在做无用功。
- 教训：黄金标准（reference/guides 等）不能直接编辑，必须经 `wf-promote.sh` 附一个新 DEC 晋升，正文也不能自带 `## 来源` 行（由晋升脚本写入）；否则来源标注与治理协议对不上，事后审计无法追溯。
