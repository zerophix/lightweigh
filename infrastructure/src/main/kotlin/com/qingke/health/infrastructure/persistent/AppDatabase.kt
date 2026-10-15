package com.qingke.health.infrastructure.persistent

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qingke.health.infrastructure.persistent.dao.MeasurementDao
import com.qingke.health.infrastructure.persistent.po.MeasurementPo

/**
 * Room 数据库入口。schema 纪律：改实体必须 version+1 + 写 Migration + 导出 schema 快照入库。
 */
@Database(entities = [MeasurementPo::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "qingke.db",
                ).build().also { instance = it }
            }
    }
}
