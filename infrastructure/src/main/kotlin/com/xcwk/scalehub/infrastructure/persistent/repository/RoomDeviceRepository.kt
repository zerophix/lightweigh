package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.sync.PendingSyncPort
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.DeviceDao
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo
import java.util.Locale

/**
 * repository — 已绑定秤仓储。
 * 解绑写 unboundAt，不删行、不删历史测量（DEC-S15）。devices() 只返回仍绑定的秤。
 */
class RoomDeviceRepository(
    private val dao: DeviceDao,
    private val pending: PendingSyncPort? = null,
) : DeviceRepository {

    private var cache: List<ScaleDevicePo>? = null

    private fun rows(): List<ScaleDevicePo> = cache ?: dao.all().also { cache = it }

    private fun boundRows(): List<ScaleDevicePo> = rows().filter { it.unboundAt == null }

    override fun devices(): List<ScaleDevice> = boundRows().map { it.toModel() }

    override fun allDevices(): List<ScaleDevice> = rows().map { row ->
        val model = row.toModel()
        if (row.unboundAt != null) model.withActive(false) else model
    }

    override fun contains(mac: String): Boolean = device(mac) != null

    override fun device(mac: String): ScaleDevice? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        return boundRows().firstOrNull { it.mac == normalized }?.toModel()
    }

    /** 新增或更新一台秤，并把它设为当前测量设备（旧库 bind :119-145）。重绑同 MAC 清 unboundAt。 */
    override fun bind(name: String, mac: String, protocolId: String): ScaleDevice? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        val protocol = protocolId.ifEmpty { ScaleDevice.PROTOCOL_OKOK_1 }
        val displayName = name.ifBlank { "智能体脂秤" }
        val algorithm = ScaleDevice.algorithmFor(protocol, ScaleDevice.ALGORITHM_BUILTIN_1)
        val now = System.currentTimeMillis()
        val existing = rows().firstOrNull { it.mac == normalized }
        val boundAt = if (existing == null || existing.boundAt.isEmpty()) today() else existing.boundAt
        val updated = ScaleDevicePo(
            normalized, displayName, boundAt, true, protocol, algorithm, now, null,
        )
        for (row in rows()) {
            if (row.mac == normalized) {
                dao.upsert(updated)
            } else if (row.active && row.unboundAt == null) {
                dao.upsert(row.copy(active = false, updatedAt = now))
            }
        }
        if (existing == null) dao.upsert(updated)
        cache = null
        pending?.enqueue("devices", normalized, "upsert")
        return device(normalized)
    }

    override fun activate(mac: String) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        if (rows().none { it.mac == normalized && it.unboundAt == null }) return
        val now = System.currentTimeMillis()
        for (row in rows()) {
            if (row.unboundAt != null) continue
            val on = row.mac == normalized
            if (row.active != on) dao.upsert(row.copy(active = on, updatedAt = now))
        }
        cache = null
        pending?.enqueue("devices", normalized, "upsert")
    }

    /** 写 unboundAt + active=0。历史测量行不在本仓储，调用方也不删它们。 */
    override fun unbind(mac: String) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        val row = rows().firstOrNull { it.mac == normalized } ?: return
        val now = System.currentTimeMillis()
        dao.upsert(row.copy(active = false, unboundAt = now, updatedAt = now))
        cache = null
        pending?.enqueue("devices", normalized, "upsert")
    }

    override fun restoreAll(devices: List<ScaleDevice>) {
        val now = System.currentTimeMillis()
        cache = null
        val incomingMacs = devices.map { normalizeMac(it.mac) }.filter { it.isNotEmpty() }.toSet()
        for (row in dao.all()) {
            if (row.mac !in incomingMacs && row.unboundAt == null) {
                dao.upsert(row.copy(active = false, unboundAt = now, updatedAt = now))
            }
        }
        for (device in devices) {
            val mac = normalizeMac(device.mac)
            if (mac.isEmpty()) continue
            dao.upsert(
                ScaleDevicePo(
                    mac, device.name, device.boundAt, device.active,
                    device.protocolId, device.algorithmId, now, null,
                ),
            )
            pending?.enqueue("devices", mac, "upsert")
        }
        cache = null
    }

    private fun ScaleDevicePo.toModel() =
        ScaleDevice(name, mac, boundAt, active, protocolId, algorithmId)

    private fun today(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date())
    }

    companion object {
        /** MAC 规范化归 domain 纯函数（MacFormat），此别名保留调用点语义。 */
        fun normalizeMac(raw: String?): String = com.xcwk.scalehub.domain.model.MacFormat.normalize(raw)
    }
}
