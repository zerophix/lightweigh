package com.xcwk.scalehub.application.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用调试日志环形缓冲（DEC-039/040）：ble/session/sync/cloud/ai/auth 全链路排障共用。
 *
 * - 容量 [MAX_LINES] 行（2 万），超出丢最旧——悬浮调试面板与一键复制共用此数据源。
 * - 敏感信息（token/验证码/完整手机号）禁止入日志，只记状态与错误类别。
 * - application 层纯 JVM 实现（不触 android.Log），infrastructure 写入、UI 层读取；
 *   AndroidBleLink 侧保留 Log.d 双写，logcat 行为不变。
 * - 线程安全：BLE 回调在 binder 线程写入，UI 在主线程读取，全程一把锁。
 */
object DebugLog {

    /** 面板与缓冲共用的行数上限（需求：最大 2 万行）。 */
    const val MAX_LINES: Int = 20_000

    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES / 4)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 追加一行；满 [MAX_LINES] 时丢弃最旧行。 */
    fun add(tag: String, message: String) {
        synchronized(lock) {
            val line = "${timeFormat.format(Date())} [$tag] $message"
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
    }

    /** 当前全部行的快照（拷贝，调用方可安全遍历）。 */
    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    /** 当前行数（面板标题显示用）。 */
    fun size(): Int = synchronized(lock) { lines.size }

    /** 清空缓冲（面板「清空」按钮）。 */
    fun clear() = synchronized(lock) { lines.clear() }
}
