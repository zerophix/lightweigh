package com.xcwk.scalehub.feature.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneOffset

/**
 * Health Connect 实现（T-059）。
 *
 * 只写体重（WRITE_WEIGHT）。体脂率在 Health Connect 里需要 BodyFatRecord 与
 * 额外权限，本轮不写，避免"部分字段成功、部分字段静默丢掉"。
 *
 * 依赖坐标：`androidx.health.connect:connect-client`。
 * 注意不是 `androidx.health:health-connect-client`——后者最高只有 1.0.0-alpha03，
 * 与代码使用的 `androidx.health.connect.client.*` 包名也不一致，曾因此被误判为
 * "缺 Google Maven 仓库"而整段注释掉。
 */
class HealthConnectPlatform(private val context: Context) : HealthPlatformPort {

    private val writePermission = HealthPermission.getWritePermission(WeightRecord::class)

    override fun availability(): HealthPlatformAvailability =
        mapSdkStatus(HealthConnectClient.getSdkStatus(context))

    override suspend fun write(samples: List<HealthSample>): HealthWriteResult {
        if (samples.isEmpty()) return HealthWriteResult.Success(0)
        if (availability() != HealthPlatformAvailability.Ready) {
            return HealthWriteResult.NotAvailable
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrElse { return HealthWriteResult.Failure("读取健康平台授权失败：${it.message}") }
        if (writePermission !in granted) {
            return HealthWriteResult.Failure("尚未授权写入体重数据")
        }
        val records = samples.map { sample ->
            WeightRecord(
                time = Instant.ofEpochMilli(sample.epochMillis),
                zoneOffset = ZoneOffset.UTC,
                weight = Mass.kilograms(sample.weightKg),
                // connect-client 1.1.0 起 Metadata 构造器为 internal，只能走工厂方法；
                // 体脂秤数据属设备自动记录，故用 autoRecorded。
                metadata = Metadata.autoRecorded(
                    device = Device(
                        manufacturer = "lightweigh",
                        model = "scale",
                        type = Device.TYPE_SCALE,
                    ),
                ),
            )
        }
        return runCatching {
            client.insertRecords(records)
            HealthWriteResult.Success(records.size)
        }.getOrElse { HealthWriteResult.Failure("写入失败：${it.message}") }
    }
}

/**
 * SDK 状态 -> 可用性。抽成纯函数便于单测：
 * 1 = 可用；2 = 需要更新 provider；3 = 设备不支持；其余按不支持处理。
 */
fun mapSdkStatus(status: Int): HealthPlatformAvailability = when (status) {
    HealthConnectClient.SDK_AVAILABLE -> HealthPlatformAvailability.Ready
    else -> HealthPlatformAvailability.Unavailable
}
