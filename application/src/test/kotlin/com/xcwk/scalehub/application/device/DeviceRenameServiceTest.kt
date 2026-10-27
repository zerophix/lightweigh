package com.xcwk.scalehub.application.device

import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 设备重命名服务行为测试。
 */
class DeviceRenameServiceTest {

    private class FakeDeviceRepo(
        var list: MutableList<ScaleDevice> = mutableListOf(),
    ) : DeviceRepository {
        var bound: Triple<String, String, String>? = null

        override fun devices(): List<ScaleDevice> = list
        override fun contains(mac: String): Boolean = list.any { it.mac == mac }
        override fun device(mac: String): ScaleDevice? = list.firstOrNull { it.mac == mac }

        override fun bind(name: String, mac: String, protocolId: String): ScaleDevice? {
            val existing = list.firstOrNull { it.mac == mac }
            if (existing != null) {
                bound = Triple(name, mac, protocolId)
                val updated = ScaleDevice(name, mac, existing.boundAt, existing.active, protocolId, existing.algorithmId)
                list.replaceAll { if (it.mac == mac) updated else it }
                return updated
            }
            // 添加新设备
            val newDevice = ScaleDevice(name, mac, "", true, protocolId, ScaleDevice.ALGORITHM_BUILTIN_1)
            list.add(newDevice)
            bound = Triple(name, mac, protocolId)
            return newDevice
        }

        override fun activate(mac: String) {
            list.replaceAll { if (it.mac == mac) it.withActive(true) else it }
        }

        override fun unbind(mac: String) {
            list.removeAll { it.mac == mac }
        }

        override fun restoreAll(devices: List<ScaleDevice>) {
            list = devices.toMutableList()
        }
    }

    @Test
    fun `rename with empty name returns error`() = runTest {
        val repo = FakeDeviceRepo()
        repo.list.add(ScaleDevice("旧名称", "AA:BB", "", true))
        val service = DeviceRenameService(repo)

        val result = service.rename("AA:BB", "  ")

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("不能为空")
    }

    @Test
    fun `rename with too long name returns error`() = runTest {
        val repo = FakeDeviceRepo()
        repo.list.add(ScaleDevice("旧名称", "AA:BB", "", true))
        val service = DeviceRenameService(repo)

        val result = service.rename("AA:BB", "a".repeat(25))

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("最多")
    }

    @Test
    fun `rename existing device succeeds`() = runTest {
        val repo = FakeDeviceRepo()
        repo.list.add(ScaleDevice("旧名称", "AA:BB", "", true))
        val service = DeviceRenameService(repo)

        val result = service.rename("AA:BB", "新名称")

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("修改")
    }

    @Test
    fun `rename nonexistent device returns error`() = runTest {
        val repo = FakeDeviceRepo()
        val service = DeviceRenameService(repo)

        val result = service.rename("CC:DD", "新名称")

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("不存在")
    }

    @Test
    fun `bind new device returns success`() = runTest {
        val repo = FakeDeviceRepo()
        val service = DeviceRenameService(repo)

        val result = service.bind("AA:BB", "1", "体脂秤")

        assertThat(result.ok).isTrue()
        assertThat(result.device?.mac).isEqualTo("AA:BB")
    }

    @Test
    fun `bind with empty name returns error`() = runTest {
        val repo = FakeDeviceRepo()
        val service = DeviceRenameService(repo)

        val result = service.bind("AA:BB", "1", "")

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("名称")
    }
}