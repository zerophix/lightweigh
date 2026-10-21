package com.xcwk.scalehub.infrastructure.persistent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xcwk.scalehub.infrastructure.persistent.dao.DeviceDao
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.dao.SyncStateDao
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo
import com.xcwk.scalehub.infrastructure.persistent.po.SyncStatePo

/**
 * Room 数据库入口。schema 纪律：改实体必须 version+1 + 写 Migration + 导出 schema 快照入库。
 */
@Database(
    entities = [MeasurementPo::class, HealthLogPo::class, ScaleDevicePo::class, SyncStatePo::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun healthLogDao(): HealthLogDao
    abstract fun deviceDao(): DeviceDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        /**
         * v1 → v2：补体脂计算输入快照三列（可空，历史行为空=未记录）。
         * 列名必须与实体字段同名（ageYears/heightCm 驼峰）——G3 连接测试曾抓到
         * 本处误写蛇形 age_years/height_cm 导致升级库与 schema 校验失败（2026-10-16）。
         * 对抗评审 R2-Q5：只存最终体脂率的历史无法解释，故把 male/age/height 随记录持久化。
         */
        /** v1→v2 三列（可空）；抽为常量供静态对拍测试，migrate 与测试单一事实源。 */
        const val MIGRATION_1_2_SQL =
            "ALTER TABLE measurements ADD COLUMN male INTEGER;" +
                "ALTER TABLE measurements ADD COLUMN ageYears INTEGER;" +
                "ALTER TABLE measurements ADD COLUMN heightCm INTEGER"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.split(";").forEach { stmt -> db.execSQL(stmt.trim()) }
            }
        }

        /**
         * v2 → v3：建 health_logs 表（DEC-022 组 C）。
         * 复合主键 (loggedAt, type) 对齐云表 UNIQUE(owner, logged_at, type)。
         * 验证状态如实声明：建表 DDL 仅与 schema 3.json 做静态对拍（组 C 测试），
         * 真实迁移执行（v2 旧库升级+数据保留）待 T-005 androidTest（G3）关闭。
         */
        /** 建表 DDL 抽为常量：与 schema 3.json 的 createSql 做静态对拍测试。 */
        const val CREATE_HEALTH_LOGS_TABLE =
            "CREATE TABLE IF NOT EXISTS `health_logs` (`loggedAt` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`value` REAL NOT NULL, `value2` REAL NOT NULL, `label` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "PRIMARY KEY(`loggedAt`, `type`))"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_HEALTH_LOGS_TABLE)
            }
        }

        /**
         * v3 → v4：建 devices 表（M3c，DEC-022）。
         * 列名与实体字段同名（mac 驼峰纪律，沿 G3 教训）；协议/算法只存本机。
         */
        const val CREATE_DEVICES_TABLE =
            "CREATE TABLE IF NOT EXISTS `devices` (`mac` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`boundAt` TEXT NOT NULL, `active` INTEGER NOT NULL, `protocolId` TEXT NOT NULL, " +
                "`algorithmId` TEXT NOT NULL, PRIMARY KEY(`mac`))"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_DEVICES_TABLE)
            }
        }

        /**
         * v4 → v5：多成员维（移植补齐）——两表补 member_id 列，默认 '1' 即默认成员，
         * 存量行归属成员 1（旧库 memberKey 升级语义）。
         */
        const val MIGRATION_4_5_SQL =
            "ALTER TABLE measurements ADD COLUMN memberId TEXT NOT NULL DEFAULT '1';" +
                "ALTER TABLE health_logs ADD COLUMN memberId TEXT NOT NULL DEFAULT '1'"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.split(";").forEach { stmt -> db.execSQL(stmt.trim()) }
            }
        }

        /**
         * v5 → v6：建 sync_state 表（M2b 水位线/对账/幂等）。
         * 复合主键 (tableName, memberId)——每表每成员独立水位线，多成员隔离的基础。
         */
        const val CREATE_SYNC_STATE_TABLE =
            "CREATE TABLE IF NOT EXISTS `sync_state` (`tableName` TEXT NOT NULL, `memberId` TEXT NOT NULL, " +
                "`pushedWatermark` INTEGER NOT NULL, `pulledWatermark` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`tableName`, `memberId`))"

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_SYNC_STATE_TABLE)
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "scalehub.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // devices 沿旧库 SP 同步直读语义（表容量个位数）；其余仓储保持 suspend
                    .allowMainThreadQueries()
                    .build().also { instance = it }
            }
    }
}
