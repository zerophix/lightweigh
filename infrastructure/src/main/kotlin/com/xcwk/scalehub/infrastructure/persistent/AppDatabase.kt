package com.xcwk.scalehub.infrastructure.persistent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/**
 * Room 数据库入口。schema 纪律：改实体必须 version+1 + 写 Migration + 导出 schema 快照入库。
 */
@Database(entities = [MeasurementPo::class, HealthLogPo::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun healthLogDao(): HealthLogDao

    companion object {
        /**
         * v1 → v2：补体脂计算输入快照三列（可空，历史行为空=未记录）。
         * 对抗评审 R2-Q5：只存最终体脂率的历史无法解释，故把 male/age/height 随记录持久化。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN male INTEGER")
                db.execSQL("ALTER TABLE measurements ADD COLUMN age_years INTEGER")
                db.execSQL("ALTER TABLE measurements ADD COLUMN height_cm INTEGER")
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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "scalehub.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
