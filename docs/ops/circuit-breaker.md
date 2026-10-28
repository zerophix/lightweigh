# 熔断与降级预案

> 版本：v1.0 | 负责人：Ops Team | 审批：DEC-028 | 关联：T-1021

## 1. 熔断原则

> **熔断 ≠ 暂停服务**：在依赖服务不可用时，快速失败并返回降级内容，
> 而不是让客户端阻塞在网络调用上拖垮整个应用。

### 1.1 熔断触发条件

| 服务/场景 | 熔断条件 | 熔断时间窗口 | 冷却时间 |
|---|---|---|---|
| CloudBase /feedback 提交 | 连续 5 次 5xx / 超时 | 60 秒内触发 | 30 秒 |
| CloudBase /measure_metrics 查询 | 连续 3 次超时 (>5s) | 30 秒内触发 | 15 秒 |
| BLE 扫描/连接 | 连续 5 次失败 | 10 秒内触发 | 5 秒 |
| AI 报告生成 | 连续 2 次 5xx | 立即触发 | 60 秒 |

### 1.2 熔断状态机

```
CLOSED ——》 OPEN ——（冷却后）——> HALF_OPEN ——成功——> CLOSED
                                   ——失败——> OPEN (重置计时)
```

- **CLOSED**: 正常请求，计数器重置
- **OPEN**: 快速失败，返回降级响应，所有请求不经过网络
- **HALF_OPEN**: 放过 1 个 probe 请求，根据结果决定恢复或重新熔断

## 2. 降级策略

### 2.1 体测数据

| 功能 | 正常模式 | 降级模式 | 用户影响 |
|---|---|---|---|
| 同步历史数据 | 从云端拉取最新 | 读取本地缓存 | 数据可能落后 24h |
| 提交测量 | 实时上云 | 写入本地队列，等待恢复后重试 | 暂时不可见其他设备 |
| AI 报告 | 实时生成 | 返回上次生成的报告 | 内容可能过期 |

### 2.2 用户反馈

| 功能 | 正常模式 | 降级模式 | 用户影响 |
|---|---|---|---|
| 提交反馈 | 实时提交到客服系统 | 保存到本地，等待恢复后重试 | 客服回复延迟 |
| 查询工单 | 实时查询 | 读取本地缓存 | 状态可能落后 |

### 2.3 蓝牙配对

| 功能 | 正常模式 | 降级模式 | 用户影响 |
|---|---|---|---|
| 设备扫描 | 实时 BLE 扫描 | 使用上次扫描缓存的设备 | 可能漏掉新设备 |
| 固件更新 | 实时查询最新版 | 使用本地缓存的版本号 | 可能错过更新 |

## 3. 熔断实现

### 3.1 Android 端

```
// CircuitBreaker.kt (infrastructure)
class CircuitBreaker(
    val failureThreshold: Int = 5,
    val timeoutMs: Long = 60_000,
    val cooldownMs: Long = 30_000,
) {
    fun isOpen(): Boolean
    fun recordSuccess()
    fun recordFailure()
    suspend fun <T> execute(block: suspend () -> T): T  // 抛出 BreakerOpenException
}
```

与 `StructuredLogger` 协作，上报熔断事件到 `structured_logs` 表。

### 3.2 CloudBase 函数

```
// 在 cloudbase/functions/ 中使用相同逻辑
// 依赖 CloudBase 计时器函数 + shared/circuit-breaker.ts
```

## 4. 应急预案

### 4.1 云端完全不可用 (P0)

1. **立即**: 在 CloudBase 控制台下线受影响函数版本，路由到上一个稳定版本
2. **次日**: 在 `remote_config` 中设置 `maintenance_mode=true`
3. **检查**: `scripts/ops/load-test.sh` 验证云端恢复
4. **恢复**: `remote_config` 设置 `maintenance_mode=false`

### 4.2 数据库性能下降 (P1)

1. **立即**: 开启 `StructuredLogger` INFO 采样（rate=1.0）
2. **排查**: 查询 `sync_metrics` 表，筛选 `error_code`
3. **缓解**: 在 `remote_config` 中设置 `sync_batch_size=20`（减半）
4. **恢复**: 性能恢复后恢复 `sync_batch_size=40`

### 4.3 AI 服务异常 (P1)

1. **立即**: `remote_config` 设置 `feature_ai_report=false`
2. **降级**: 客户端使用上次生成的报告
3. **通知**: 推送通知告知用户服务维护
4. **恢复**: AI 服务恢复后，批量重新生成过期报告

## 5. 回退清单

| 操作 | 回退方式 | 责任人 |
|---|---|---|
| CloudBase 函数版本 | 控制台回滚到上一个别名 | 运维工程师 |
| RemoteConfig 配置 | 设置默认值覆盖 | 产品运营 |
| 应用商店版本 | 紧急下架 + 热更 | 产品运营 |

## 来源: docs/ai/decisions/DEC-028