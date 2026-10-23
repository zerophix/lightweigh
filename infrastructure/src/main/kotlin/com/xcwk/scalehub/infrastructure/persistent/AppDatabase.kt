package com.xcwk.scalehub.infrastructure.persistent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xcwk.scalehub.infrastructure.persistent.dao.DeviceDao
import com.xcwk.scalehub.infrastructure.persistent.dao.GoalDao
import com.xcwk.scalehub.infrastructure.persistent.dao.HabitDao
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogSyncDao
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementSyncDao
import com.xcwk.scalehub.infrastructure.persistent.dao.MemberDao
import com.xcwk.scalehub.infrastructure.persistent.dao.PendingSyncDao
import com.xcwk.scalehub.infrastructure.persistent.dao.SyncStateDao
import com.xcwk.scalehub.infrastructure.persistent.po.GoalPo
import com.xcwk.scalehub.infrastructure.persistent.po.HabitPo
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo
import com.xcwk.scalehub.infrastructure.persistent.po.MemberPo
import com.xcwk.scalehub.infrastructure.persistent.po.PendingSyncPo
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo
import com.xcwk.scalehub.infrastructure.persistent.po.SyncStatePo

/**
 * Room 数据库入口。schema 纪律：改实体必须 version+1 + 写 Migration + 导出 schema 快照入库。
 * v8：DEC-057，对齐已签 Notion DEC-S01…S16。
 */
@Database(
    entities = [
        MeasurementPo::class,
        HealthLogPo::class,
        ScaleDevicePo::class,
        SyncStatePo::class,
        HabitPo::class,
        GoalPo::class,
        MemberPo::class,
        PendingSyncPo::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun measurementSyncDao(): MeasurementSyncDao
    abstract fun healthLogDao(): HealthLogDao
    abstract fun healthLogSyncDao(): HealthLogSyncDao
    abstract fun habitDao(): HabitDao
    abstract fun deviceDao(): DeviceDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun goalDao(): GoalDao
    abstract fun memberDao(): MemberDao
    abstract fun pendingSyncDao(): PendingSyncDao

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

        /** v6 → v7：建 habits 表（P0-2 习惯管理页）。 */
        const val CREATE_HABITS_TABLE =
            "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`period` TEXT NOT NULL, `targetCount` INTEGER NOT NULL, " +
                "`doneCount` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, " +
                "`reminderHour` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`memberId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_HABITS_TABLE)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_memberId` ON `habits` (`memberId`)")
            }
        }

        /**
         * v7 → v8（DEC-057）：签过的 DEC-S01…S16 目标态。
         *
         * 测量表重建（PK 不能 ALTER）：recordId UUID、updatedAt=measuredAt、deletedAt NULL、
         * syncStatus 0、note ''，UNIQUE(memberId, measuredAt)。
         * health_logs 重建：PK (memberId, loggedAt, type)，updatedAt=loggedAt。
         * devices 加 updatedAt / unboundAt（boundAt 仍 TEXT，云列跟进见 DEC）。
         * 新建 goals / members / pending_sync。habits 索引已在 6→7 创建，这里 IF NOT EXISTS 对齐。
         * SP（goals/members）不在 SQLite 里，由 Store 首次打开导入。
         */
        const val MIGRATION_7_8_MEASUREMENTS =
            "CREATE TABLE IF NOT EXISTS `measurements_new` (`recordId` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, " +
                "`weightKg` REAL NOT NULL, `impedanceOhm` REAL NOT NULL, `fat` REAL NOT NULL, `mac` TEXT NOT NULL, " +
                "`algorithmId` TEXT NOT NULL, `male` INTEGER, `ageYears` INTEGER, `heightCm` INTEGER, " +
                "`memberId` TEXT NOT NULL DEFAULT '1', `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "`syncStatus` INTEGER NOT NULL DEFAULT 0, `note` TEXT NOT NULL DEFAULT '', " +
                "PRIMARY KEY(`recordId`))"

        const val MIGRATION_7_8_HEALTH_LOGS =
            "CREATE TABLE IF NOT EXISTS `health_logs_new` (`loggedAt` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`value` REAL NOT NULL, `value2` REAL NOT NULL, `label` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`memberId` TEXT NOT NULL DEFAULT '1', `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`memberId`, `loggedAt`, `type`))"

        /** 与导出 schema 对拍；编译后若 Room createSql 有出入，以 8.json 为准回写本常量。 */
        const val CREATE_GOALS_TABLE_V8 =
            "CREATE TABLE IF NOT EXISTS `goals` (`memberId` TEXT NOT NULL, `targetWeightKg` REAL NOT NULL DEFAULT 0, " +
                "`calorieGoal` INTEGER NOT NULL DEFAULT 2049, `waterGoal` INTEGER NOT NULL DEFAULT 2000, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`memberId`))"

        const val CREATE_MEMBERS_TABLE_V8 =
            "CREATE TABLE IF NOT EXISTS `members` (`id` TEXT NOT NULL, `slot` INTEGER NOT NULL, " +
                "`nickname` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val CREATE_PENDING_SYNC_TABLE_V8 =
            "CREATE TABLE IF NOT EXISTS `pending_sync` (`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, " +
                "`op` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `attempts` INTEGER NOT NULL, " +
                "`nextRetryAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entityType`, `entityId`))"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_7_8_MEASUREMENTS)
                db.execSQL(
                    "INSERT INTO measurements_new (recordId, measuredAt, weightKg, impedanceOhm, fat, mac, " +
                        "algorithmId, male, ageYears, heightCm, memberId, updatedAt, deletedAt, syncStatus, note) " +
                        "SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                        "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', (abs(random()) % 4) + 1, 1) || " +
                        "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))), " +
                        "measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId, male, ageYears, heightCm, " +
                        "memberId, measuredAt, NULL, 0, '' FROM measurements",
                )
                db.execSQL("DROP TABLE measurements")
                db.execSQL("ALTER TABLE measurements_new RENAME TO measurements")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_measurements_memberId_measuredAt` " +
                        "ON `measurements` (`memberId`, `measuredAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurements_updatedAt` ON `measurements` (`updatedAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurements_syncStatus` ON `measurements` (`syncStatus`)",
                )

                db.execSQL(MIGRATION_7_8_HEALTH_LOGS)
                db.execSQL(
                    "INSERT INTO health_logs_new (loggedAt, type, value, value2, label, note, memberId, updatedAt, deletedAt) " +
                        "SELECT loggedAt, type, value, value2, label, note, memberId, loggedAt, NULL FROM health_logs",
                )
                db.execSQL("DROP TABLE health_logs")
                db.execSQL("ALTER TABLE health_logs_new RENAME TO health_logs")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_logs_updatedAt` ON `health_logs` (`updatedAt`)",
                )

                db.execSQL("ALTER TABLE devices ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN unboundAt INTEGER")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_memberId` ON `habits` (`memberId`)")

                db.execSQL(CREATE_GOALS_TABLE_V8)
                db.execSQL(CREATE_MEMBERS_TABLE_V8)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_members_slot` ON `members` (`slot`)",
                )
                db.execSQL(CREATE_PENDING_SYNC_TABLE_V8)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_sync_nextRetryAt` ON `pending_sync` (`nextRetryAt`)",
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "scalehub.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                    // devices 沿旧库 SP 同步直读语义（表容量个位数）；其余仓储保持 suspend
                    .allowMainThreadQueries()
                    .build().also { instance = it }
            }
    }
}
