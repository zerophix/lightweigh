package com.qingke.health.trigger.ui

import com.qingke.health.infrastructure.persistent.repository.InMemoryMeasurementRepository

/**
 * 守卫负例夹具 — 故意违反 "trigger.ui 不得依赖 infrastructure" 规则。
 * 仅存在于测试源码。禁止在任何生产代码引用本类。
 */
@Suppress("unused")
class RuleViolationFixture {
    val bad: InMemoryMeasurementRepository = InMemoryMeasurementRepository()
}
