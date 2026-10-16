# qk-health-v2 — 轻刻健康 Android（DDD 脚手架工程版）

基于 your-android-scaffold 生成的生产业务工程。混合式重构路线：
旧库 qk-health-android 冻结为行为参考，好层移植、烂层重写。

## 快速开始

    # 构建 + 全部测试（含 ArchUnit 守卫）
    ./gradlew build

    # Android Studio：Open 选择本目录，Gradle JDK 选 embedded jbr-21

## 当前里程碑（M1：可运行骨架）

| 层 | 状态 |
|---|---|
| :domain | 移植旧库协议17+算法4+模型4（包名脚手架化），黄金向量测试守护 |
| :application | RecordMeasurement 用例（语义与旧库 finishSave 逐位一致：fat=-1 哨兵/age 守卫/算法回落） |
| :infrastructure | Room 存储（measurements 按 spec/contract 契约，取代 SP JSON 与180条截断）+ ProfileStore |
| :app | UDF 首屏（手动记体重+最近列表）+ ArchUnit 守卫6条（含负例+解析自检） |

## 与旧库/契约的关系

- 行为参考：../qk-health-android（冻结，含 BLE/云同步/完整 UI 的真机验证行为）
- 跨端契约：../spec/（黄金向量 TSV 同源、data-schema 字段契约、naming-map）
- 协议知识：../proto-okok/docs/notes/（NOTE-20261014-001 含 8 电极系数表）

## 路线图

- M2：SyncGateway 云同步 + 登录（参考旧库 auth/cloud/sync 行为，防腐层隔离 CloudBase）
- M3：ScaleGateway BLE 称重（三协议接入 domain 已移植的解析器；状态机参照 ref-openscale）
- M4：完整页面（绑定/趋势/报告/日志/设置）+ 档案编辑
- M5：8 电极 isEightR 分支（系数见 NOTE-20261014-001）+ iOS 按 naming-map 镜像

## 构建

    ./gradlew build          # 含全部测试与 ArchUnit 守卫

JDK 17+（Android Studio JBR 21）、Android SDK 34。applicationId: com.xcwk.scalehub。

## 相关文档

- [AGENTS.md](AGENTS.md) — AI 协作协议与治理规则
- [STATUS.md](STATUS.md) — 自动生成的项目状态看板
- [docs/](docs/index.md) — 架构/决策/生命周期完整文档站
- 跨端契约：../spec/（黄金向量、data-schema、naming-map）

## 当前状态

M1 里程碑完成：四模块可构建、13 测试全绿、APK 产出。治理模板已安装并通过全量校验。

### 反思

本次安装与恢复踩到五个坑，学到的教训：

1. setup.sh 的复制循环不递归丢了 modules 校验包，装完必须立刻 validate，安装成功不等于系统可用。
2. 自己改白名单丢闭括号且修复断言写太巧没修上，修语法用最笨的先看现场。
3. **根因级教训：emoji 修复器处理了自己的定义文件，剥掉 KEYCAP 正则里的键帽字面量，
   使模式退化为可吞掉任意数字/#/* 的自指炸弹。已锚定 KEYCAP_RE 并加金丝雀测试防复发。**
4. 非交互管道下 setup 的问答与 conda 分支行为与终端不同，先定位卡点再猜。
5. 排查损坏先做字节级签名比对定位集合，再用 mtime 时间线锁定操作，比猜测快得多。
6. 清 WARN 时踩到规则优先级：「当前状态」必须是最后一个二级章节，相关文档只能放它前面——改文档结构前先读对应检查器的判定顺序。
7. 治理视野会随工具链生长：Android Studio 一打开就生成 local.properties，白名单要跟着工程生态更新，否则工具产物会被当成治理违规。
8. zsh 的 ${d/a/b} 参数替换在含转义斜杠的路径上不会做替换，反而会造出名字里带反斜杠的怪目录——批量路径重命名必须交给脚本语言做确定性操作，别用 shell 内建替换。
9. 生命周期门禁的价值在这轮兑现：源码变更被强制携带 DEC+DevNotes 同车，决策记录的时机被钉死在改动发生的时刻，事后补录直接被拦。
