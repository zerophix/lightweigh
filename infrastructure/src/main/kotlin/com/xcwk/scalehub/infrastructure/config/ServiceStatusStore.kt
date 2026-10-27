package com.xcwk.scalehub.infrastructure.config

import android.content.Context
import com.xcwk.scalehub.application.config.ServiceStatusPort
import com.xcwk.scalehub.application.config.ServiceStatus
import com.xcwk.scalehub.infrastructure.config.BackendConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 服务状态实现（远程配置）。
 *
 * 从 /v1/config/status 查询维护模式/版本信息。
 * 本地缓存 5 分钟。
 */
class ServiceStatusStore(
    private val context: Context,
) : ServiceStatusPort {

    private val _status = MutableStateFlow(
        ServiceStatus(false, "服务正常", 0L, 0L, false, "unknown"),
    )

    private var lastFetch = 0L

    override suspend fun fetchStatus(): ServiceStatus {
        // 本地缓存检查
        val now = System.currentTimeMillis()
        if (now - lastFetch < 300_000L) {
            return _status.value
        }
        return refresh()
    }

    override suspend fun refresh(): ServiceStatus {
        val status = try {
            val conn = URL("${BackendConfig.BASE}/v1/config/status").openConnection() as HttpURLConnection
            conn.connectTimeout = BackendConfig.CONNECT_TIMEOUT_MS.toInt()
            conn.readTimeout = BackendConfig.READ_TIMEOUT_MS.toInt()
            conn.setRequestProperty("Content-Type", "application/json")
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(text)
                ServiceStatus(
                    maintenance = json.optBoolean("maintenance", false),
                    message = json.optString("message", "服务正常"),
                    startTime = json.optLong("start_time", 0L),
                    endTime = json.optLong("end_time", 0L),
                    forced = json.optBoolean("forced", false),
                    version = json.optString("version", "unknown"),
                )
            } else {
                ServiceStatus(false, "网络异常，请稍后重试", 0L, 0L, false, "unknown")
            }
        } catch (e: Exception) {
            ServiceStatus(false, "无法连接服务器", 0L, 0L, false, "unknown")
        }

        _status.value = status
        lastFetch = System.currentTimeMillis()
        return status
    }

    override fun statusFlow(): Flow<ServiceStatus> = _status
}
