package com.xcwk.scalehub.infrastructure.monitoring

import android.os.SystemClock
import android.util.Log
import com.xcwk.scalehub.BuildConfig
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 启动性能上报（T-1003）。
 *
 * 职责：
 *  - 测量 App 冷启动耗时（Application.onCreate → 首帧渲染完成）
 *  - 测量关键生命周期节点耗时（Splash → FirstDraw）
 *  - 上报到 CloudBase /startup_perf 表
 *  - 采样上报（默认 100%）
 *
 * 使用方式：
 *  - Application.onCreate: StartupPerfReporter.markAppStart()
 *  - Activity 首帧渲染后: StartupPerfReporter.markFirstRender()
 */
class StartupPerfReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "StartupPerfReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val STARTUP_PERF_TABLE = "startup_perf"

        private const val PROP_COLD_START_MS = "cold_start_ms"
        private const val PROP_FIRST_RENDER_MS = "first_render_ms"
        private const val PROP_TOTAL_STARTUP_MS = "total_startup_ms"
        private const val PROP_APP_VERSION = "app_version"
        private const val PROP_BUILD_TYPE = "build_type"
        private const val PROP_DEVICE_MODEL = "device_model"
        private const val PROP_SDK_INT = "sdk_int"
        private const val PROP_TIMESTAMP = "timestamp"
        private const val PROP_SAMPLED = "sampled"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appStartNanos: Long? = null

    @Volatile
    private var firstRenderTimestamp: Long? = null

    /**
     * 在 Application.onCreate 调用（标记冷启动开始）。
     */
    fun markAppStart() {
        if (appStartNanos != null) return  // 热启动不重复标记
        appStartNanos = SystemClock.elapsedRealtimeNanos()
        Log.d(TAG, "app start marked")
    }

    /**
     * 在 Activity 首帧渲染完成后调用。
     */
    fun markFirstRender() {
        if (firstRenderTimestamp != null) return
        firstRenderTimestamp = System.currentTimeMillis()
        Log.d(TAG, "first render marked")
    }

    /**
     * 上报启动性能指标。
     * 在首帧渲染完成后调用。
     */
    fun reportStartup() {
        val startNanos = appStartNanos ?: return
        val renderTime = firstRenderTimestamp ?: System.currentTimeMillis()

        val coldStartMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000
        val firstRenderMs = try {
            (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000 - coldStartMs
        } catch (e: Exception) { 0L }

        val totalStartupMs = (renderTime - java.util.Date().time + coldStartMs) // approximate
        val sampled = Math.random() < samplingRate

        if (!sampled) {
            Log.d(TAG, "startup perf sampled out")
            return
        }

        val fields = mapOf(
            PROP_COLD_START_MS to coldStartMs,
            PROP_FIRST_RENDER_MS to System.currentTimeMillis() - renderTime,
            PROP_TOTAL_STARTUP_MS to coldStartMs,
            PROP_APP_VERSION to BuildConfig.VERSION_NAME,
            PROP_BUILD_TYPE to if (BuildConfig.DEBUG) "debug" else "release",
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
            PROP_SDK_INT to android.os.Build.VERSION.SDK_INT,
            PROP_TIMESTAMP to System.currentTimeMillis(),
            PROP_SAMPLED to sampled,
        )

        DebugLog.add("monitoring", "startup: cold=${coldStartMs}ms")

        scope.launch {
            val result = cloud?.upsert(
                STARTUP_PERF_TABLE,
                "timestamp",
                listOf(CloudRow(fields))
            )
            if (result == true) {
                Log.i(TAG, "startup perf uploaded: cold=${coldStartMs}ms")
            } else {
                Log.w(TAG, "startup perf upload failed (offline?)")
            }
        }
    }

    /**
     * 获取冷启动时间（用于本地调试）。
     */
    fun coldStartMs(): Long {
        val start = appStartNanos ?: return -1L
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
    }
}
