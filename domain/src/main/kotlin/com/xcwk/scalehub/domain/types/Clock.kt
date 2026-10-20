package com.xcwk.scalehub.domain.types

/**
 * 时间源（DEC-034 组 C / F-006）。
 *
 * 「时间戳由用例盖章、存储层不盖章」本是领域规则，此前却靠每个写用例自觉调
 * `System.currentTimeMillis()` 来维持——新增用例容易忘记，或改用 `Instant.now()` /
 * `System.nanoTime()` 造成口径不一；测试想钉死时间只能 mock 静态方法。
 *
 * 收成接口后，聚合根工厂方法内部取时间，**用例拿不到时钟也就无法绕过盖章规则**。
 *
 * 注意范围：只约束「新建聚合」这一路径。其余的 `currentTimeMillis` 属不同语义、
 * 不在此列——协议时间包下发（TimePacket）、查询侧算「今天」（HomeGoalQuery）、
 * token 过期判断（SessionStore）、日期选择器初值（HistoryScreen）。
 */
interface Clock {
    fun nowMillis(): Long
}

/** 生产实现：系统时钟。 */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** 测试实现：时间固定，让时间相关断言可确定性复现。 */
class FixedClock(private var millis: Long) : Clock {
    override fun nowMillis(): Long = millis

    /** 推进时间（模拟两次记录之间的间隔）。 */
    fun advance(deltaMillis: Long) {
        millis += deltaMillis
    }
}
