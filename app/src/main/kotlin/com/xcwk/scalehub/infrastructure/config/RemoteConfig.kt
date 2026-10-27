package com.xcwk.scalehub.infrastructure.config

import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudDataPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 远程配置/开关（T-1018）。
 *
 * 职责：
 *  - 从 CloudBase /remote_config 表获取远程配置
 *  - 本地缓存配置，设置 TTL
 *  - 提供响应式流，UI 可订阅配置变更
 *  - 支持 A/B 测试（按用户 ID 分桶）
 *  - 紧急下线开关（kill switch）
 */
class RemoteConfig(
    private val cloud: CloudDataPort? = null,
) {

    companion object {
        private const val TAG = "RemoteConfig"
        private const val CONFIG_TABLE = "remote_config"
        private const val CACHE_TTL_MS = 300_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _config = MutableStateFlow<Map<String, String>>(emptyMap())
    val configFlow: StateFlow<Map<String, String>> = _config

    @Volatile
    private var lastFetch = 0L
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        fetch()
    }

    fun fetch(force: Boolean = false) {
        if (force) lastFetch = 0L
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_TTL_MS && _config.value.isNotEmpty()) return
        lastFetch = now

        scope.launch {
            val result = fetchRemote()
            if (result.isNotEmpty()) {
                _config.value = result
                DebugLog.add("config", "remote config updated: ${result.size} keys")
            }
        }
    }

    private suspend fun fetchRemote(): Map<String, String> {
        if (cloud == null) return emptyMap()
        val rows = cloud.rows(CONFIG_TABLE, "select=*&status=active") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (row in rows) {
            val key = row.fields["key"]?.toString() ?: continue
            val value = row.fields["value"]?.toString() ?: continue
            result[key] = value
        }
        return result
    }

    fun string(key: String, default: String = ""): String = _config.value[key] ?: default
    fun bool(key: String, default: Boolean = false): Boolean = _config.value[key]?.toBoolean() ?: default
    fun int(key: String, default: Int = 0): Int = _config.value[key]?.toIntOrNull() ?: default

    fun abTest(userId: String, bucketCount: Int = 100): Int = (userId.hashCode() and 0x7fffffff) % bucketCount

    fun isABTestUser(userId: String, rate: Double): Boolean = abTest(userId, 10000) < (rate * 10000)

    fun isMaintenanceMode(): Boolean = bool("maintenance_mode", false)
    fun isForcedUpdate(): Boolean = bool("force_update", false)
    fun minAppVersion(): String = string("min_app_version", "1.0.0")
    fun isFeatureEnabled(feature: String): Boolean = bool("feature_$feature", false)
}
