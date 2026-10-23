package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xcwk.scalehub.infrastructure.persistent.po.GoalPo

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE memberId = :memberId LIMIT 1")
    fun get(memberId: String): GoalPo?

    @Upsert
    fun upsert(row: GoalPo)

    @Query("DELETE FROM goals")
    fun deleteAll()
}
