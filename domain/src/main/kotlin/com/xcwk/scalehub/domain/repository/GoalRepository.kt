package com.xcwk.scalehub.domain.repository

/**
 * 目标存储（旧库 HealthLogStore 目标段 :136-146 移植，按成员隔离）。
 * calorieGoal 默认 2049、waterGoal 默认 2000（旧库默认值，推送时按默认值跳过）。
 * 存储权威是 Room `goals`（DEC-057 / DEC-S02）。calorie/water 仍随 profile 推送；
 * targetWeight 本阶段不写入云 payload（云列未落地，见 DEC-057 云跟进）。
 */
interface GoalRepository {
    /** 目标体重（kg）；0=未设置。 */
    var targetWeight: Float

    var calorieGoal: Int

    var waterGoal: Int
}
