package com.xcwk.scalehub.feature.measure

import com.xcwk.scalehub.infrastructure.persistent.repository.InMemoryMeasurementRepository

/**
 * 守卫负例夹具 — 故意违反 "feature 不得依赖 infrastructure" 规则。
 * 仅存在于测试源码。禁止在任何生产代码引用本类。
 */
@Suppress("unused")
class RuleViolationFixture {
    val bad: InMemoryMeasurementRepository = InMemoryMeasurementRepository()
}
