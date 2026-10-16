package com.xcwk.scalehub.domain.model

import java.util.Locale

/**
 * MAC 规范化（旧库 AppStore.normalizeMac :208-227 移植）：
 * 剥离分隔符，12 位十六进制重排为大写冒号对；不合法返回空串。
 * 归 domain 纯函数：同步合并（application）与设备仓储（infra）共用。
 */
object MacFormat {
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val hex = raw.replace(Regex("[^0-9A-Fa-f]"), "").uppercase(Locale.US)
        if (hex.length != 12) return ""
        return (0 until 12 step 2).joinToString(":") { hex.substring(it, it + 2) }
    }
}
