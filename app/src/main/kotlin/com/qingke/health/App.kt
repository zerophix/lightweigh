package com.qingke.health

import android.content.Context
import android.app.Application
import com.qingke.health.application.query.MeasurementQuery
import com.qingke.health.application.service.RecordMeasurementService
import com.qingke.health.domain.repository.MeasurementRepository
import com.qingke.health.infrastructure.persistent.AppDatabase
import com.qingke.health.infrastructure.persistent.repository.RoomMeasurementRepository
import com.qingke.health.infrastructure.profile.ProfileStore

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
    val profileStore: ProfileStore = ProfileStore(context)

    val recordMeasurementService: RecordMeasurementService by lazy {
        RecordMeasurementService(measurementRepository, profileStore)
    }
    val measurementQuery: MeasurementQuery by lazy { MeasurementQuery(measurementRepository) }
}
