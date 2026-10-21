package com.xcwk.scalehub.infrastructure.persistent.repository

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * PO <-> 领域读写映射回归（DEC-022 组 B 同车修复）。
 *
 * 背景：R2 的 toDomain 快照透传因替换锚点不匹配静默 no-op 而未入库，
 * 读路径丢失 male/ageYears/heightCm 三字段直至组 B 才被发现——
 * 本测试经写口（save）与查询实现（RoomMeasurementQuery.between）钉住写读两端字段保真，
 * 再发生单侧映射改动即红。
 */
class RoomMappingTest {

    private class CapturingDao : MeasurementDao {
        val saved = mutableListOf<MeasurementPo>()
        var toReturn: List<MeasurementPo> = emptyList()
        override suspend fun upsert(po: MeasurementPo) {
            saved += po
            toReturn = saved.toList()
        }
        override suspend fun recent(limit: Int, memberId: String): List<MeasurementPo> =
            toReturn.sortedByDescending { it.measuredAt }.take(limit)
        override suspend fun between(from: Long, to: Long, memberId: String): List<MeasurementPo> =
            toReturn.filter { it.measuredAt >= from && it.measuredAt < to }
        override suspend fun delete(time: Long) {
            saved.removeAll { it.measuredAt == time }
            toReturn = saved.toList()
        }
    }

    @Test
    fun `写读映射保真_输入快照与核心字段不丢失`() = runTest {
        val dao = CapturingDao()
        val repo = RoomMeasurementRepository(dao, { "1" })
        val record = MeasureRecord(
            1700000000000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1",
            true, 30, 175,
        )
        repo.save(record, "member-1")
        assertThat(dao.saved).hasSize(1)
        assertThat(dao.saved[0].male).isTrue()
        assertThat(dao.saved[0].memberId).isEqualTo("member-1")
        assertThat(dao.saved[0].ageYears).isEqualTo(30)
        assertThat(dao.saved[0].heightCm).isEqualTo(175)

        val back = RoomMeasurementQuery(dao, { "1" }).between(0L, Long.MAX_VALUE).single()
        assertThat(back.time).isEqualTo(record.time)
        assertThat(back.weight).isWithin(1e-9).of(70.0)
        assertThat(back.fat).isWithin(1e-9).of(18.4)
        assertThat(back.algorithm).isEqualTo("okok-bia-1")
        assertThat(back.male).isTrue()          // 回归钉：toDomain 曾丢这三个字段
        assertThat(back.ageYears).isEqualTo(30)
        assertThat(back.heightCm).isEqualTo(175)
    }

    @Test
    fun `旧数据无快照读为null_兼容语义`() = runTest {
        val dao = CapturingDao()
        val repo = RoomMeasurementRepository(dao, { "1" })
        // 模拟 v1 旧行为：仅 6 字段（经 PO 直塞）
        dao.toReturn = listOf(
            MeasurementPo(1690000000000L, 68.0, 400.0, -1.0, "CC:DD", "builtin-1", null, null, null, "1"),
        )
        val back = RoomMeasurementQuery(dao, { "1" }).between(0L, Long.MAX_VALUE).single()
        assertThat(back.male).isNull()
        assertThat(back.ageYears).isNull()
        assertThat(back.heightCm).isNull()
    }
}
