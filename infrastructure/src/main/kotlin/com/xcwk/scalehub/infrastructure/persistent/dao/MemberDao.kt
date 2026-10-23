package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xcwk.scalehub.infrastructure.persistent.po.MemberPo

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY slot ASC")
    fun all(): List<MemberPo>

    @Query("SELECT COUNT(*) FROM members")
    fun count(): Int

    @Upsert
    fun upsert(row: MemberPo)

    @Query("DELETE FROM members WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM members")
    fun deleteAll()

    @Query(
        "UPDATE members SET active = CASE WHEN id = :id THEN 1 ELSE 0 END, updatedAt = :now",
    )
    fun setActive(id: String, now: Long)
}
