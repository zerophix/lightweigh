# qk-health-v2 — 轻刻健康 Android（DDD 脚手架工程版）

基于 your-android-scaffold 生成的**生产业务工程**。混合式重构路线：
旧库 qk-health-android 冻结为行为参考，好层移植、烂层重写。

## 当前里程碑（M1：可运行骨架）

| 层 | 状态 |
|---|---|
| `:domain` | ✅ 移植旧库协议(17)+算法(4)+模型(4)（包名脚手架化），黄金向量测试守护 |
| `:application` | ✅ RecordMeasurement 用例（语义与旧库 finishSave 逐位一致：fat=-1 哨兵/age 守卫/算法回落） |
| `:infrastructure` | ✅ Room 存储（measurements 按 spec/contract 契约，取代 SP JSON+180条截断）+ ProfileStore |
| `:app` | ✅ UDF 首屏（手动记体重+最近列表）+ ArchUnit 守卫 6 条（含负例+解析自检） |

## 与旧库/契约的关系

- **行为参考**：`../qk-health-android`（冻结，含 BLE/云同步/完整 UI 的真机验证行为）
- **跨端契约**：`../spec/`（黄金向量 TSV 同源、data-schema 字段契约、naming-map）
- **协议知识**：`../proto-okok/docs/notes/`（NOTE-20261014-001 含 8 电极系数表）

## 路线图

- M2：SyncGateway 云同步 + 登录（参考旧库 auth/cloud/sync 行为，防腐层隔离 CloudBase）
- M3：ScaleGateway BLE 称重（三协议接入 domain 已移植的解析器；状态机参照 ref-openscale）
- M4：完整页面（绑定/趋势/报告/日志/设置）+ 档案编辑
- M5：8 电极 isEightR 分支（系数见 NOTE-20261014-001，向量已占位）+ iOS 按 naming-map 镜像

## 构建

```bash
./gradlew build          # 含全部测试与 ArchUnit 守卫
```
JDK 17+（Android Studio JBR 21）、Android SDK 34。applicationId: com.qingke.health。
