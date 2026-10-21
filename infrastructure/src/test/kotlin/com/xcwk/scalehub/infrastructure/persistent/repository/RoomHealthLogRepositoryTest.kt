package com.xcwk.scalehub.infrastructure.persistent.repository

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.types.DomainException
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** 健康记录仓储往返与脏行 fail-fast（DEC-022 组 C）；读路径经 RoomHealthLogQuery（T-016 读写分离）。 */
class RoomHealthLogRepositoryTest {

    private class CapturingDao : HealthLogDao {
        val saved = mutableListOf<HealthLogPo>()
        var toReturn: List<HealthLogPo> = emptyList()
        override suspend fun upsert(po: HealthLogPo) {
            saved += po
            toReturn = saved.toList()
        }
        override suspend fun recent(limit: Int, memberId: String): List<HealthLogPo> =
            toReturn.sortedByDescending { it.loggedAt }.take(limit)
        override suspend fun between(from: Long, to: Long, memberId: String): List<HealthLogPo> =
            toReturn.filter { it.loggedAt >= from && it.loggedAt < to }.sortedBy { it.loggedAt }
    }

    @Test
    fun `写读往返_六字段保真`() = runTest {
        val dao = CapturingDao()
        val repo = RoomHealthLogRepository(dao, { "1" })
        val log = HealthLog(1700000000000L, "diet", 1500.0, 0.0, "午餐", "米饭")
        repo.save(log, "1")
        assertThat(dao.saved).hasSize(1)
        assertThat(dao.saved[0].loggedAt).isEqualTo(log.time)
        assertThat(dao.saved[0].memberId).isEqualTo("1")
        assertThat(dao.saved[0].type).isEqualTo("diet")

        val back = RoomHealthLogQuery(dao, { "1" }).recent(10).single()
        assertThat(back).isEqualTo(log)
    }

    @Test
    fun `读取脏行_未知类型failfast`() {
        val dao = CapturingDao()
        val repo = RoomHealthLogRepository(dao, { "1" })
        dao.toReturn = listOf(HealthLogPo(1700000000000L, "walk", 1.0, 0.0, "", "", "1"))
        assertThrows(DomainException::class.java) {
            runBlocking { RoomHealthLogQuery(dao, { "1" }).recent(1) }
        }
    }
}
