# SLO 定义与阈值

> 版本：v1.0 | 负责人：Ops Team | 审批：DEC-085

## 1. 概述

本文档定义轻衡体脂秤 App 的 SLO（Service Level Objective）与告警阈值。
所有 SLO 来源于用户旅程关键路径，阈值基于历史基线 + 业务容忍度。

## 2. SLO 列表

### 2.1 启动性能 (StartupPerfReporter → startup_perf)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 冷启动耗时 | p95 ≤ 3000ms | p95 > 3500ms | Application.onCreate → 首帧渲染 |
| 首帧渲染耗时 | p95 ≤ 800ms | p95 > 1000ms | Splash/Activity 切换 |
| 启动失败率 | ≤ 0.1% | > 0.5% | 崩溃占比 |

### 2.2 测量 (MeasureMetricsReporter → measure_metrics)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 测量成功率 | ≥ 98% | < 95% | 一次测量流程完成 |
| 测量平均耗时 | ≤ 5000ms | > 8000ms | 开始→结果页 |
| 测量超时率 | ≤ 2% | > 5% | 5s+ 未完成 |
| 无效数据率 | ≤ 3% | > 8% | 阻抗异常导致重试 |

### 2.3 同步 (SyncMetricsReporter → sync_metrics)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 同步成功率 | ≥ 99% | < 97% | push/pull 成功率 |
| 同步平均耗时 | ≤ 5000ms | > 10000ms | 批量 40 行上云 |
| 冲突率 | ≤ 1% | > 3% | 需要手动解决的比例 |
| 认证过期率 | ≤ 0.5% | > 1% | token 过期导致失败 |

### 2.4 BLE 连接 (BleMetricsReporter → ble_metrics)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 连接成功率 | ≥ 95% | < 90% | 扫描→连接成功 |
| 连接平均耗时 | ≤ 3000ms | > 5000ms | 扫描→连接完成 |
| 掉线率 | ≤ 2% | > 5% | 已连设备在测量中断开 |
| 超时率 | ≤ 3% | > 8% | 5s+ 未连接 |

### 2.5 崩溃 (CrashReporter → crash_reports)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 崩溃率 | ≤ 0.1% | > 0.3% | 每日活跃用户中崩溃占比 |
| ANR 率 | ≤ 0.05% | > 0.15% | 主线程卡死率 |

### 2.6 后台服务 (ServiceStatusPort → service_status)

| 指标 | SLO | 告警阈值 | 注释 |
|---|---|---|---|
| 维护模式时长 | ≤ 30min/d | > 60min/d | 灰度部署窗口 |
| 强制下线率 | ≤ 0.01% | > 0.05% | forced=true 比例 |

## 3. 告警分级

| 等级 | 响应时间 | 通知对象 | 处理 |
|---|---|---|---|
| P0 严重 | 5 分钟 | 全体 On-Call 群 + 电话 | 立即排查，回滚或降级 |
| P1 重要 | 30 分钟 | 值班工程师 | 查看日志，评估影响 |
| P2 警告 | 2 小时 | 团队群 | 日常优化，下班前跟进 |
| P3 提示 | 24 小时 | 邮件摘要 | 长期趋势，定期优化 |

## 4. 告警规则映射

| 指标 | 规则 ID | 表达式 | 等级 |
|---|---|---|---|
| 崩溃率 | ALERT-CRASH-001 | `rate(crash_reports[15m]) > 0.003` | P0 |
| ANR 率 | ALERT-ANR-001 | `rate(anr_reports[15m]) > 0.0015` | P1 |
| 启动耗时 p95 | ALERT-STARTUP-001 | `histogram_quantile(0.95, startup_perf_bucket[15m]) > 3500` | P1 |
| 测量成功率 | ALERT-MEASURE-001 | `rate(measure_metrics{result!="SUCCESS"}[15m]) / rate(measure_metrics[15m]) > 0.05` | P1 |
| 同步成功率 | ALERT-SYNC-001 | `rate(sync_metrics{result!="SUCCESS"}[15m]) / rate(sync_metrics[15m]) > 0.03` | P1 |
| BLE 连接成功率 | ALERT-BLE-001 | `rate(ble_metrics{event="CONNECT_END",success="false"}[15m]) / rate(ble_metrics{event="CONNECT_START"}[15m]) > 0.10` | P1 |
| 冲突率 | ALERT-CONFLICT-001 | `rate(sync_metrics{result="FAIL_CONFLICT"}[15m]) / rate(sync_metrics[15m]) > 0.03` | P2 |

## 5. 应急预案

### 5.1 崩溃率暴涨 (P0)
1. 查看 `crash_reports` 表，按 `error_code` 聚合
2. 确定堆栈：CrashReporter.sanitizeStackTrace 过滤敏感信息
3. 若为版本回归 → 立即回滚或灰度下线
4. 若为第三方 SDK → 紧急降级或移除

### 5.2 同步失败率升高 (P1)
1. 查看 `sync_metrics` 表，按 `direction` / `error_code` 分析
2. 区分是网络问题（FAIL_NETWORK）还是服务端问题（FAIL_SERVER）
3. 若为 AUTH 过期 → 通知刷新 token 机制
4. 若为 SERVER → 联系后端团队紧急排查

### 5.3 BLE 掉线率升高 (P1)
1. 查看 `ble_metrics` 表，按 `disconnect_reason` 分析
2. 若为 CONNECTION_LOST 为多 → 疑似固件或信号问题
3. 若为 SERVICE_DISCOVERY_FAILED → 检查设备协议兼容性

## 6. 采样策略

| 指标类型 | 采样率 | 理由 |
|---|---|---|
| CrashReporter | 100% | 崩溃是低频事件，不能丢弃 |
| AnrMonitor | 100% | ANR 是低频事件 |
| MeasureMetricsReporter | 100%（≤100条/min） | 测量是用户核心行为 |
| SyncMetricsReporter | 100% | 同步是低频操作 |
| BleMetricsReporter | 100%（≤200条/min） | BLE 事件有频次限制 |

超出速率限制时，优先保留错误事件（非成功事件）。

## 来源: docs/ai/decisions/DEC-085
