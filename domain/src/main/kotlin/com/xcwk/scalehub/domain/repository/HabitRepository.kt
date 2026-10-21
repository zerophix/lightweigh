package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitItem

/**
 * 习惯读取端口（列表/详情/当期进度）。
 * 多成员：所有查询按当前成员隔离（memberId 隐式传递，不泄露给调用方）。
 */
interface HabitReader {
    /** 当前成员的所有习惯（按创建时间倒序）。 */
    fun all(): List<HabitItem>

    /** 单条习惯详情（含完整字段，编辑页用）。 */
    fun get(id: String): Habit?
}

/**
 * 习惯写入端口（新建/编辑/删除/打卡）。
 * 保存后由容器侧事件订阅广播 DataChange.Habits（DEC-034 组 A）。
 * 提醒调度：保存/删除后由容器侧 HabitReminderScheduler 调度/取消 WorkManager 任务。
 */
interface HabitWriter {
    /** 新建或更新（id 存在即更新，保留 createdAt/打卡进度）。 */
    suspend fun save(habit: Habit)

    /** 物理删除。 */
    suspend fun delete(id: String)

    /** 打卡：completedCount +1（同一周期内幂等，防重复点击）。 */
    suspend fun tick(id: String): Boolean
}

/** 组合端口（仓储实现同时满足读写）。 */
interface HabitRepository : HabitReader, HabitWriter