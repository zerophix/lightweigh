package com.xcwk.scalehub.infrastructure.monitoring

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ANR 监控（T-1002）。
 *
 * 职责：
 *  - 监听主线程卡死（通过 Handler 检测主线程是否响应）
 *  - 记录 ANR 时间、发生前最后日志
 *  - 上报到 CloudBase /anr_reports 表
 *  - 不写入敏感信息
 *
 * 实现原理：启动一个后台线程定期检查主线程是否能及时处理消息。
 * 如果主线程在 [CHECK_INTERVAL_MS] 内未响应，判定为 ANR。
 */
class AnrMonitor(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "AnrMonitor"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val ANR_REPORTS_TABLE = "anr_reports"

        /** ANR 检测阈值（毫秒）。Android 默认 5 秒。 */
        private const val ANR_THRESHOLD_MS = 5_000L

        /** 检测间隔（毫秒）。 */
        private const val CHECK_INTERVAL_MS = 1_000L

        private const val PROP_BLOCKED_AT = "blocked_at"
        private const val PROP_STACK_TRACE = "main_thread_trace"
        private const val PROP_LAST_LOGS = "last_logs"
        private const val PROP_THREAD_DUMP = "thread_dump"
        private const val PROP_TIMESTAMP = "timestamp"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var monitoring = false
    @Volatile
    private var lastResponseTime = System.currentTimeMillis()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 开始监控。调用一次即可。
     */
    fun start() {
        if (monitoring) return
        monitoring = true
        lastResponseTime = System.currentTimeMillis()
        Thread {
            while (monitoring) {
                val now = System.currentTimeMillis()
                val blockedDuration = now - lastResponseTime
                if (blockedDuration > ANR_THRESHOLD_MS) {
                    // 检测到可能的 ANR
                    val stackTrace = captureMainThreadStack()
                    val lastLogs = DebugLog.snapshot().takeLast(20)
                    onAnrDetected(now, blockedDuration, stackTrace, lastLogs)
                }
                try { Thread.sleep(CHECK_INTERVAL_MS) } catch (e: InterruptedException) { break }
            }
        }.apply {
            name = "ANR-Monitor"
            start()
        }
        // 定期更新 lastResponseTime（通过主线程消息队列）
        schedulePing()
        Log.i(TAG, "ANR monitor started")
    }

    /**
     * 停止监控。
     */
    fun stop() {
        monitoring = false
        Log.i(TAG, "ANR monitor stopped")
    }

    /**
     * 定期 ping 主线程，如果能处理则更新 lastResponseTime。
     */
    private fun schedulePing() {
        mainHandler.post(object : Runnable {
            override fun run() {
                lastResponseTime = System.currentTimeMillis()
                if (monitoring) {
                    mainHandler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        })
    }

    /**
     * 捕获主线程堆栈。
     */
    private fun captureMainThreadStack(): String {
        val mainThread = Looper.getMainLooper().thread
        return try {
            mainThread.stackTrace.joinToString("\n") { it.toString() }
        } catch (e: Exception) {
            "unable to capture: ${e.message}"
        }
    }

    /**
     * ANR 检测回调。
     */
    private fun onAnrDetected(
        time: Long,
        blockedDuration: Long,
        stackTrace: String,
        lastLogs: List<String>,
    ) {
        Log.w(TAG, "ANR detected: blocked for ${blockedDuration}ms")
        DebugLog.add("monitoring", "ANR: blocked ${blockedDuration}ms")

        if (Math.random() >= samplingRate) {
            Log.w(TAG, "ANR report sampled out")
            return
        }

        val fields = mapOf(
            PROP_BLOCKED_AT to blockedDuration,
            PROP_STACK_TRACE to stackTrace.take(10_000),
            PROP_LAST_LOGS to lastLogs.joinToString("\n"),
            PROP_THREAD_DUMP to Thread.getAllStackTraces().entries.joinToString("\n") { (thread, frames) ->
                "${thread.name}: ${frames.joinToString { frame -> frame.toString() }}"
            },
            PROP_TIMESTAMP to time,
        )

        scope.launch {
            val result = cloud?.upsert(
                ANR_REPORTS_TABLE,
                "timestamp",
                listOf(CloudRow(fields))
            )
            if (result == true) {
                Log.i(TAG, "ANR report uploaded")
                // 标记已上报，避免重复
                lastResponseTime = System.currentTimeMillis()
            } else {
                Log.w(TAG, "ANR report upload failed")
            }
        }
    }
}
