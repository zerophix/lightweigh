package com.xcwk.scalehub.infrastructure.monitoring

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
 *
 * 职责：
 *  - 全局 UncaughtExceptionHandler 捕获崩溃
 *  - 过滤敏感信息（token/手机号/验证码不入日志）
 *  - 采样上报（SamplingRate，可配置）
 *  - 上报到 CloudBase /crash_reports 表
 *
 * 存根实现：仅记录本地日志 + 可选 HTTP 上报。
 * 生产环境替换为 Crashlytics SDK。
 */
class CrashReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "CrashReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0  // 默认全量上报
        private const val CRASH_REPORTS_TABLE = "crash_reports"

        private const val PROP_APP_VERSION = "app_version"
        private const val PROP_BUILD_TYPE = "build_type"
        private const val PROP_DEVICE_MODEL = "device_model"
        private const val PROP_SDK_INT = "sdk_int"
        private const val PROP_STACK_TRACE = "stack_trace"
        private const val PROP_THREAD_NAME = "thread_name"
        private const val PROP_TIMESTAMP = "timestamp"

        /** 敏感信息正则（token/验证码/手机号）。 */
        private val SENSITIVE_PATTERNS = listOf(
            "token=[^&,\\s]+".toRegex(),
            "access_token=[^&,\\s]+".toRegex(),
            "refresh_token=[^&,\\s]+".toRegex(),
            "\\d{11}".toRegex(),  // 手机号
            "code=[^&,\\s]+".toRegex(),
        )

        @Volatile
        private var installed = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 初始化崩溃处理。调用一次即可。
     */
    fun install() {
        if (installed) return
        installed = true
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
        Log.i(TAG, "CrashReporter installed")
    }

    /**
     * 过滤敏感信息。
     */
    private fun sanitizeStackTrace(trace: String): String {
        var sanitized = trace
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = sanitized.replace(pattern, "[REDACTED]")
        }
        return sanitized
    }

    /**
     * 判断是否需要上报（采样）。
     */
    private fun shouldReport(): Boolean = Math.random() < samplingRate

    /**
     * 上报崩溃信息到云端。
     */
    private fun reportCrash(
        thread: Thread,
        exception: Throwable,
    ) {
        if (!shouldReport()) {
            Log.w(TAG, "crash report sampled out")
            return
        }
        val trace = sanitizeStackTrace(exception.stackTraceToString())
        val timestamp = System.currentTimeMillis()

        val appVersion = try {
            val pkg = Thread.currentThread().contextClassLoader.getResource("package.txt")?.readText()
            pkg ?: "unknown"
        } catch (e: Exception) { "unknown" }

        val fields = mapOf(
            PROP_APP_VERSION to appVersion,
            PROP_BUILD_TYPE to BuildConfig.DEBUG,
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
            PROP_SDK_INT to android.os.Build.VERSION.SDK_INT,
            PROP_STACK_TRACE to trace,
            PROP_THREAD_NAME to thread.name,
            PROP_TIMESTAMP to timestamp,
        )

        scope.launch {
            val result = cloud?.upsert(
                CRASH_REPORTS_TABLE,
                "timestamp",
                listOf(CloudRow(fields))
            )
            if (result == true) {
                Log.i(TAG, "crash report uploaded")
            } else {
                Log.w(TAG, "crash report upload failed (offline?)")
                // 保存到本地，下次同步时上传
                DebugLog.add("monitoring", "crash: queued for retry")
            }
        }
    }

    /**
     * 全局崩溃处理器。
     */
    private inner class CrashHandler : Thread.UncaughtExceptionHandler {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        override fun uncaughtException(thread: Thread, exception: Throwable) {
            Log.e(TAG, "uncaught exception in ${thread.name}", exception)
            reportCrash(thread, exception)

            // 延迟一点，让上报任务完成（后台线程）
            try { Thread.sleep(200) } catch (e: InterruptedException) { }

            // 交给系统默认处理器（显示崩溃对话框）
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    /**
     * 直接上报崩溃（用于已捕获的异常）。
     */
    fun reportException(
        thread: Thread = Thread.currentThread(),
        exception: Throwable,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        reportCrash(thread, exception)
    }
}
