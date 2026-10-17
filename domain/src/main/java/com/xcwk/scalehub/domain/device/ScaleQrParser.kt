package com.xcwk.scalehub.domain.device

import com.xcwk.scalehub.domain.model.MacFormat

/**
 * 体脂秤二维码解析（旧库 ScaleQr.java :19-120 移植）：
 * 支持纯 MAC、扁平 JSON（mac/deviceMac/address/ble + name/deviceName/model）、
 * 带参数链接（mac/device/address + name/deviceName）三种形态。
 * 偏差声明：旧库用 org.json 与 android.net.Uri；domain 为纯 JVM 模块，
 * 改用正则取键值——QR 载荷为扁平结构，语义等价。
 */
data class ScaleQr(val name: String, val mac: String) {
    companion object {
        private val MAC = Regex("(?i)(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}")
        private val HEX12 = Regex("(?i)(?<![0-9a-f])[0-9a-f]{12}(?![0-9a-f])")

        fun parse(text: String?): ScaleQr? {
            val raw = text?.trim().orEmpty()
            if (raw.isEmpty()) return null
            if (raw.startsWith("{")) fromJson(raw)?.let { return it }
            if (raw.contains("://")) fromUri(raw)?.let { return it }
            val mac = firstMac(raw)
            if (mac.isEmpty()) return null
            return ScaleQr("", mac)
        }

        private fun fromJson(raw: String): ScaleQr? {
            var mac = firstMac(jsonValue(raw, "mac", "deviceMac", "address", "ble"))
            if (mac.isEmpty()) mac = firstMac(raw)
            if (mac.isEmpty()) return null
            return ScaleQr(jsonValue(raw, "name", "deviceName", "model"), mac)
        }

        private fun fromUri(raw: String): ScaleQr? {
            val query = raw.substringAfter('?', "")
            var mac = firstMac(queryValue(query, "mac", "device", "address"))
            if (mac.isEmpty()) mac = firstMac(raw)
            if (mac.isEmpty()) return null
            return ScaleQr(queryValue(query, "name", "deviceName"), mac)
        }

        /** 扁平 JSON 取键值（正则口径：第一个出现的键，字符串或数字值）。 */
        private fun jsonValue(raw: String, vararg keys: String): String {
            for (key in keys) {
                val match = Regex("\"$key\"\\s*:\\s*\"?([^\",}]*)\"?").find(raw) ?: continue
                val value = match.groupValues[1].trim()
                if (value.isNotEmpty()) return value
            }
            return ""
        }

        /** 链接 query 取参数。 */
        private fun queryValue(query: String, vararg keys: String): String {
            if (query.isEmpty()) return ""
            for (pair in query.split('&')) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = pair.substring(0, idx)
                if (name in keys) {
                    val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8").trim()
                    if (value.isNotEmpty()) return value
                }
            }
            return ""
        }

        private fun firstMac(raw: String): String {
            if (raw.isEmpty()) return ""
            MAC.find(raw)?.let { return MacFormat.normalize(it.value) }
            HEX12.find(raw)?.let { return MacFormat.normalize(it.value) }
            return MacFormat.normalize(raw)
        }
    }
}
