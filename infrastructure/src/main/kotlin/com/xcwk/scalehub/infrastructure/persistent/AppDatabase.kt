package com.xcwk.scalehub.infrastructure.persistent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/**
 * Room 数据库入口。schema 纪律：改实体必须 version+1 + 写 Migration + 导出 schema 快照入库。
 */
@Database(entities = [MeasurementPo::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "scalehub.db",
                ).addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
