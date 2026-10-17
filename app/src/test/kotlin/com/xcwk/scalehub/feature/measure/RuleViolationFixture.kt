package com.xcwk.scalehub.feature.measure

import com.xcwk.scalehub.infrastructure.persistent.repository.InMemoryMeasurementRepository

/**
 * 守卫负例夹具 — 故意违反 "feature 不得依赖 infrastructure" 规则。
 * 仅存在于测试源码。禁止在任何生产代码引用本类。
 */
@Suppress("unused")
class RuleViolationFixture {
    val bad: InMemoryMeasurementRepository = InMemoryMeasurementRepository()

    /** 存储 SDK 负例（T-006 上锁随附）：Room 包级 + SharedPreferences 类名级。 */
    val roomDb: androidx.room.RoomDatabase? = null
    val prefs: android.content.SharedPreferences? = null

    /** P1 桥面白名单负例：引用白名单外的根包类。 */
    val rootSneak: com.xcwk.scalehub.RootSneakFixture = com.xcwk.scalehub.RootSneakFixture()
}
