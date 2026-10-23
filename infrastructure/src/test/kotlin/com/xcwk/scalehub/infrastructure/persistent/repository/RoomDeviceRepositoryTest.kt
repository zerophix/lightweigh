package com.xcwk.scalehub.infrastructure.persistent.repository

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MacFormat
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.infrastructure.persistent.dao.DeviceDao
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo
import org.junit.Test

/**
 * 设备仓储行为（M3c，DEC-022）—— 旧库 AppStore devices 段语义：
 * 绑定即设当前并失活他机、重绑保留 boundAt、MAC 规范化、云端基线整集写入。
 */
class RoomDeviceRepositoryTest {

    private class FakeDao : DeviceDao {
        val rows = linkedMapOf<String, ScaleDevicePo>()
        override fun all(): List<ScaleDevicePo> = rows.values.sortedBy { it.boundAt }
        override fun upsert(device: ScaleDevicePo) { rows[device.mac] = device }
        override fun delete(mac: String) { rows.remove(mac) }
    }

    @Test
    fun `bind adds device and normalizes mac`() {
        val dao = FakeDao()
        val repo = RoomDeviceRepository(dao)
        val bound = requireNotNull(repo.bind("卧室秤", "aa-bb-cc-dd-ee-ff", ScaleDevice.PROTOCOL_OKOK_1))
        assertThat(bound.mac).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(bound.active).isTrue()
        assertThat(bound.protocolId).isEqualTo(ScaleDevice.PROTOCOL_OKOK_1)
        assertThat(bound.boundAt).isNotEmpty()
        assertThat(repo.contains("AA:BB:CC:DD:EE:FF")).isTrue()
    }

    @Test
    fun `bind okok protocol selects okok bia algorithm`() {
        val repo = RoomDeviceRepository(FakeDao())
        val bound = requireNotNull(repo.bind("秤", "AA:BB:CC:DD:EE:FF", "okok-1"))
        assertThat(bound.algorithmId).isEqualTo("okok-bia-1")
        val chipsea = requireNotNull(repo.bind("芯海", "11:22:33:44:55:66", "chipsea-cloud-1"))
        assertThat(chipsea.algorithmId).isEqualTo(ScaleDevice.ALGORITHM_BUILTIN_1)
    }

    @Test
    fun `rebind deactivates others and keeps boundAt`() {
        val dao = FakeDao()
        val repo = RoomDeviceRepository(dao)
        val first = requireNotNull(repo.bind("甲", "AA:BB:CC:DD:EE:FF", "okok-1"))
        val second = requireNotNull(repo.bind("乙", "11:22:33:44:55:66", "okok-1"))
        assertThat(second.active).isTrue()
        assertThat(repo.device(first.mac)!!.active).isFalse()

        val rebound = requireNotNull(repo.bind("乙改名", "11:22:33:44:55:66", "chipsea-cloud-1"))
        assertThat(rebound.boundAt).isEqualTo(second.boundAt)
        assertThat(rebound.protocolId).isEqualTo("chipsea-cloud-1")
        assertThat(rebound.name).isEqualTo("乙改名")
    }

    @Test
    fun `invalid mac is rejected`() {
        val repo = RoomDeviceRepository(FakeDao())
        assertThat(repo.bind("秤", "not-a-mac", "okok-1")).isNull()
        assertThat(repo.contains("12345")).isFalse()
        assertThat(repo.device("")).isNull()
    }

    @Test
    fun `activate and unbind toggle the fleet`() {
        val dao = FakeDao()
        val repo = RoomDeviceRepository(dao)
        repo.bind("甲", "AA:BB:CC:DD:EE:FF", "okok-1")
        repo.bind("乙", "11:22:33:44:55:66", "okok-1")
        repo.activate("AA:BB:CC:DD:EE:FF")
        assertThat(repo.device("AA:BB:CC:DD:EE:FF")!!.active).isTrue()
        assertThat(repo.device("11:22:33:44:55:66")!!.active).isFalse()
        repo.unbind("AA:BB:CC:DD:EE:FF")
        assertThat(repo.contains("AA:BB:CC:DD:EE:FF")).isFalse()
        val tombstone = dao.rows["AA:BB:CC:DD:EE:FF"]
        assertThat(tombstone).isNotNull()
        assertThat(tombstone!!.unboundAt).isNotNull()
        assertThat(tombstone.name).isEqualTo("甲")
        assertThat(tombstone.active).isFalse()
    }

    @Test
    fun `restoreAll replaces the fleet verbatim`() {
        val dao = FakeDao()
        val repo = RoomDeviceRepository(dao)
        repo.bind("甲", "AA:BB:CC:DD:EE:FF", "okok-1")
        repo.restoreAll(
            listOf(
                ScaleDevice("云端秤", "66:55:44:33:22:11", "2026-10-02", false),
                ScaleDevice("云端秤2", "11:22:33:44:55:66", "2026-10-03", true),
            ),
        )
        assertThat(repo.devices().map { it.mac })
            .containsExactly("66:55:44:33:22:11", "11:22:33:44:55:66")
        assertThat(repo.device("11:22:33:44:55:66")!!.active).isTrue()
        assertThat(repo.contains("AA:BB:CC:DD:EE:FF")).isFalse()
    }

    @Test
    fun `mac format matches legacy normalize`() {
        assertThat(MacFormat.normalize("aa-bb-cc-dd-ee-ff")).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(MacFormat.normalize("AABBCCDDEEFF")).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(MacFormat.normalize("AABBCCDDEEF")).isEqualTo("")
        assertThat(MacFormat.normalize(null)).isEqualTo("")
        assertThat(RoomDeviceRepository.normalizeMac("aabb.ccdd.eeff")).isEqualTo("AA:BB:CC:DD:EE:FF")
    }
}
