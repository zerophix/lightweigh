package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xcwk.scalehub.infrastructure.persistent.po.HabitPo
import kotlinx.coroutines.flow.Flow

/**
 * dao — habits 表访问（DEC-043-D 习惯管理页）。
 * 习惯数量少（个位数到几十），但按成员隔离，读路径须带 memberId 过滤。
 * 列表查询返回 Flow，支持实时刷新（习惯变更/成员切换）。
 */
@Dao
interface HabitDao {
    /** 某成员全部习惯，创建时间正序（响应式）。 */
    @Query("SELECT * FROM habits WHERE memberId = :memberId ORDER BY createdAt ASC")
    fun all(memberId: String): Flow<List<HabitPo>>

    /** 同步一次性查询（HabitRepository.all 消费）。 */
    @Query("SELECT * FROM habits WHERE memberId = :memberId AND active = 1 ORDER BY createdAt ASC")
    fun allList(memberId: String): List<HabitPo>

    /** 按 id 取单条。 */
    @Query("SELECT * FROM habits WHERE id = :id AND memberId = :memberId")
    fun byId(id: String, memberId: String): HabitPo?

    /** 新增或覆盖（主键 id 冲突覆盖）。 */
    @Upsert
    fun upsert(po: HabitPo)

    /** 物理删除。 */
    @Query("DELETE FROM habits WHERE id = :id")
    fun delete(id: String)
}
