package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo

/** dao — devices 表访问（数量为个位数，查询按绑定时间排序）。 */
@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY boundAt")
    fun all(): List<ScaleDevicePo>

    @Upsert
    fun upsert(device: ScaleDevicePo)

    @Query("DELETE FROM devices WHERE mac = :mac")
    fun delete(mac: String)
}
