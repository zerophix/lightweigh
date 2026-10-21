package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitItem
import kotlinx.coroutines.flow.Flow

/**
 * 习惯查询端口（T-016 仓储精简 + DEC-043-D 习惯管理页）。
 *
 * 从 HabitRepository 剥离的只读查询操作移入查询端口：domain 仓储仅写（save），
 * 查询语义归查询层（同 MeasurementQueryPort / HealthLogQueryPort）。
 * 端口定义在 application、实现在 infrastructure（RoomHabitQuery）。
 */
interface HabitQueryPort {
    /** 按 id 取单条；不存在返回 null。 */
    suspend fun byId(id: String): Habit?

    /** 当前成员的所有习惯（含当期进度/达标/周期边界），响应式流。 */
    fun allFlow(): Flow<List<HabitItem>>
}

/** 习惯查询壳（UI 消费，经由端口读，不直触 infra）。 */
class HabitQuery(private val port: HabitQueryPort) {
    fun allFlow(): Flow<List<HabitItem>> = port.allFlow()
}