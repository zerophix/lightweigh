package com.xcwk.scalehub

import android.content.Context
import android.app.Application
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.ai.AiPort
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.auth.AuthService
import com.xcwk.scalehub.application.query.HealthLogQuery
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.service.RecordHealthLogService
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.application.sync.SyncService
import com.xcwk.scalehub.application.service.DeleteMeasurementService
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.infrastructure.ai.CloudFunctionAi
import com.xcwk.scalehub.infrastructure.ble.AndroidBleLink
import com.xcwk.scalehub.infrastructure.cloud.CloudBaseGateway
import com.xcwk.scalehub.infrastructure.persistent.AppDatabase
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomHealthLogRepository
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomMeasurementRepository
import com.xcwk.scalehub.infrastructure.profile.ProfileStore
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomDeviceRepository
import com.xcwk.scalehub.infrastructure.session.SessionStore

/**
 * app — 组合根：全工程唯一知道所有实现的地方。
 * 后续阶段的接线点：SyncGateway(云)、ScaleGateway(BLE) 都在这里装配。
 */
class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** 手写轻量 DI 容器（规模化后可平移 Hilt，装配点不变）。 */
class AppContainer(context: Context) {

    /** 数据变更信号（DEC-021 支柱2 实体；写用例发、查询 VM 收）。 */
    val dataRefreshBus = DataRefreshBus()

    /** 会话与认证（M2b）：同源实现双端口（会话读写 + 认证动作）。 */
    val sessionStore: SessionStore by lazy { SessionStore(context) }

    val cloudGateway: CloudBaseGateway by lazy { CloudBaseGateway(sessionStore) }

    val database: AppDatabase by lazy { AppDatabase.get(context) }

    val measurementRepository: MeasurementRepository by lazy {
        RoomMeasurementRepository(database.measurementDao())
    }
    val healthLogRepository: HealthLogRepository by lazy {
        RoomHealthLogRepository(database.healthLogDao())
    }
    val profileStore: ProfileStore = ProfileStore(context)

    val recordMeasurementService: RecordMeasurementService by lazy {
        RecordMeasurementService(measurementRepository, profileStore, dataRefreshBus)
    }
    val measurementQuery: MeasurementQuery by lazy { MeasurementQuery(measurementRepository) }
    val healthLogQuery: HealthLogQuery by lazy { HealthLogQuery(healthLogRepository) }
    val trendQuery: TrendQuery by lazy { TrendQuery(measurementRepository) }
    val recordHealthLogService: RecordHealthLogService by lazy {
        RecordHealthLogService(healthLogRepository, dataRefreshBus)
    }
    val profileQuery: ProfileQuery by lazy { ProfileQuery(profileStore) }
    val reportQuery: ReportQuery by lazy { ReportQuery(measurementRepository) }
    val updateProfileService: UpdateProfileService by lazy { UpdateProfileService(profileStore) }
    val deleteMeasurementService: DeleteMeasurementService by lazy {
        DeleteMeasurementService(measurementRepository, dataRefreshBus)
    }

    /** 已绑定秤仓储（M3c）。 */
    val deviceRepository: RoomDeviceRepository by lazy {
        RoomDeviceRepository(AppDatabase.get(context).deviceDao())
    }

    val syncService: SyncService by lazy {
        SyncService(
            sessionStore, cloudGateway, sessionStore, deviceRepository,
            measurementRepository, healthLogRepository, profileStore, profileStore,
        )
    }

    val authService: AuthService by lazy { AuthService(sessionStore, syncService) }

    /** AI 问答（M2c）：实现随 M2 部署联调（云函数 ai-chat）。 */
    val aiService: AiService by lazy { AiService(sessionStore, CloudFunctionAi(sessionStore)) }

    /** 测量页偏好（"不再提示只存体重"开关，旧库 skipWeightOnlyPrompt）。 */
    private val measurePrefs: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("measure", android.content.Context.MODE_PRIVATE)
    }

    /** "不再提示只存体重"读取口（SharedPreferences 藏在容器后，feature 层不直触）。 */
    val skipWeightOnlyPrompt: () -> Boolean = { measurePrefs.getBoolean("skip_weight_only", false) }

    /** 蓝牙链路（M3c）：测量页经 MeasureSession 使用；权限门在 trigger 层。 */
    val bleLink: AndroidBleLink by lazy { AndroidBleLink(context) }
}
