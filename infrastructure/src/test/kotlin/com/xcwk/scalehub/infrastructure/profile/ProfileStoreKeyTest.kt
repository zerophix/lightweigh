package com.xcwk.scalehub.infrastructure.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 档案键登记守卫（DEC-029 组 A）：ProfileStore 声明的 KEY_ 常量集必须与 ALL_KEYS 完全一致。
 *
 * 动机：clearMember 原实现就地枚举键名，与常量声明形成两份副本 —— 新增档案字段而漏登记，
 * 成员移除后会留孤儿键，且没有任何信号（既无编译错，也无运行时错）。
 * 现 clearMember 遍历 ALL_KEYS（单一事实源），本测试守住「常量 → ALL_KEYS」这一步不漏。
 *
 * 纯反射读 const 字段（编译为 ProfileStore 的 static final String）+ companion 属性，
 * 不实例化、不触 Android 运行时，故可作为 JVM 单测执行。
 */
class ProfileStoreKeyTest {

    @Test
    fun `KEY常量集与ALL_KEYS完全一致`() {
        val declaredKeys = ProfileStore::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.name to (it.get(null) as String) }
            .filter { (name, _) -> name.startsWith("KEY_") }
            .toMap()

        assertThat(declaredKeys.keys).isNotEmpty()

        // 双向相等：既防「有常量未登记 ALL_KEYS」，也防「ALL_KEYS 登记了不存在的键值」
        assertThat(ProfileStore.ALL_KEYS.toSet()).isEqualTo(declaredKeys.values.toSet())
        assertThat(ProfileStore.ALL_KEYS).containsNoDuplicates()
    }
}
