package com.xcwk.scalehub

import android.content.Context
import android.app.Application
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.service.RecordHealthLogService
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.infrastructure.persistent.AppDatabase
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomHealthLogRepository
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomMeasurementRepository
import com.xcwk.scalehub.infrastructure.profile.ProfileStore

/**
 * app — 组合根：全工程唯一知道所有实现的地方。
 * 后续阶段的接线点：SyncGateway(云)、ScaleGateway(BLE) 都在这里装配。
 */
class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** 手写轻量 DI 容器（规模化后可平移 Hilt，装配点不变）。 */
class AppContainer(context: Context) {

    val database: AppDatabase by lazy { AppDatabase.get(context) }

    val measurementRepository: MeasurementRepository by lazy {
        RoomMeasurementRepository(database.measurementDao())
    }
    val healthLogRepository: HealthLogRepository by lazy {
        RoomHealthLogRepository(database.healthLogDao())
    }
    val profileStore: ProfileStore = ProfileStore(context)

    val recordMeasurementService: RecordMeasurementService by lazy {
        RecordMeasurementService(measurementRepository, profileStore)
    }
    val measurementQuery: MeasurementQuery by lazy { MeasurementQuery(measurementRepository) }
    val trendQuery: TrendQuery by lazy { TrendQuery(measurementRepository) }
    val recordHealthLogService: RecordHealthLogService by lazy {
        RecordHealthLogService(healthLogRepository)
    }
}
