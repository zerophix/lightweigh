package com.xcwk.scalehub.domain.model

/**
 * 健康记录类型 —— 移植自旧库 RecordScreen 的记录入口（qk-health-android Pages.kt:466-478）。
 *
 * 旧库界面共 8 类记录入口，其中 "weight" 走 saveManualWeight 写入测量表
 * （Pages.kt:537-539），不入健康记录存储——故本枚举只有 7 类，
 * HealthLog 构造守卫对 "weight" 报错即忠实于该存储行为（DEC-022 忠实性原则 3）。
 */
enum class HealthLogType(val wire: String) {
    DIET("diet"),
    EXERCISE("exercise"),
    WATER("water"),
    WAIST("waist"),
    GLUCOSE("glucose"),
    BLOOD_PRESSURE("blood_pressure"),
    HABIT("habit");

    /** 非习惯类主值必须为正数（旧库校验「请输入有效数字」，Pages.kt:532-535）。 */
    val requiresPositiveValue: Boolean get() = this != HABIT

    /** 习惯类以 label 为记录主体（旧库 add("habit", 1.0, label=text)，Pages.kt:539）。 */
    val requiresLabel: Boolean get() = this == HABIT

    companion object {
        /** 线格式（云表 type 列/本地存储）转枚举，未知返回 null。 */
        fun fromWire(wire: String): HealthLogType? = entries.firstOrNull { it.wire == wire }
    }
}
