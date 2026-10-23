package com.xcwk.scalehub.ui

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.ui.components.searchResultSummary
import com.xcwk.scalehub.ui.components.shouldLoadMore
import com.xcwk.scalehub.ui.states.PROTOTYPE_STATE_MAP
import com.xcwk.scalehub.ui.states.QkGenericState
import com.xcwk.scalehub.ui.states.QkStateKind
import com.xcwk.scalehub.ui.states.stateKindOf
import org.junit.Test

/**
 * Wave 0 纯逻辑单测（T-097 + T-098）。
 *
 * 与 `tests/unit/test_error_states_map.py` 分工：
 * - 本文件在 JVM 侧断言**行为与分类语义**（可被 `:app:testDebugUnitTest` 执行）
 * - Python 侧与**真实原型 HTML 交叉核对 view 集合**（Kotlin 单测不便读 HTML）
 *
 * 覆盖的都是「写错了没人会发现」的判定逻辑：
 * 加载阈值、搜索文案、原型 view 分类 —— 都不靠真机手测兜底。
 */
class Wave0ComponentsTest {

    // ---- T-097：原型 view 分类 ----

    @Test
    fun `原型分类表覆盖全部 16 个 view`() {
        assertThat(PROTOTYPE_STATE_MAP).hasSize(16)
    }

    @Test
    fun `分类表没有重复的 view`() {
        val ids = PROTOTYPE_STATE_MAP.map { it.viewId }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `每个 view 都写明归属理由`() {
        val blank = PROTOTYPE_STATE_MAP.filter { it.note.isBlank() }.map { it.viewId }
        assertThat(blank).isEmpty()
    }

    @Test
    fun `业务屏不携带通用状态`() {
        val bad = PROTOTYPE_STATE_MAP
            .filter { it.kind == QkStateKind.SpecificScreen && it.state != null }
            .map { it.viewId }
        assertThat(bad).isEmpty()
    }

    @Test
    fun `通用状态必须落到具体类型`() {
        val bad = PROTOTYPE_STATE_MAP
            .filter { it.kind == QkStateKind.Generic && it.state == null }
            .map { it.viewId }
        assertThat(bad).isEmpty()
    }

    @Test
    fun `关键 view 的归类与原型语义一致`() {
        // v-sync-fail：1 条失败其余 127 条已同步 → 局部错误（AC-H4）
        val sync = stateKindOf("v-sync-fail")
        assertThat(sync).isNotNull()
        assertThat(sync!!.state).isEqualTo(QkGenericState.PartialError)

        assertThat(stateKindOf("v-pair-timeout")!!.state).isEqualTo(QkGenericState.LoadError)
        assertThat(stateKindOf("v-fb-fail")!!.state).isEqualTo(QkGenericState.LoadError)
        assertThat(stateKindOf("v-fullscreen-load")!!.state)
            .isEqualTo(QkGenericState.FullScreenLoading)

        // 业务屏必须显式为 null（明确「不适用本任务」）
        assertThat(stateKindOf("v-rec-edit")!!.state).isNull()
        assertThat(stateKindOf("v-hub")!!.state).isNull()
        assertThat(stateKindOf("v-longpress")!!.state).isNull()
    }

    @Test
    fun `未知 view 返回 null 而不是默认状态`() {
        assertThat(stateKindOf("v-not-exists")).isNull()
    }

    // ---- T-098：无限滚动触发阈值 ----

    @Test
    fun `空列表不触发加载`() {
        assertThat(shouldLoadMore(lastVisibleIndex = 0, totalItems = 0)).isFalse()
        assertThat(shouldLoadMore(lastVisibleIndex = -1, totalItems = 10)).isFalse()
    }

    @Test
    fun `距底部超过阈值时不触发`() {
        // total=10, threshold=3 → lastVisible >= 6 才触发
        assertThat(shouldLoadMore(lastVisibleIndex = 5, totalItems = 10, threshold = 3)).isFalse()
    }

    @Test
    fun `进入阈值区间即触发`() {
        assertThat(shouldLoadMore(lastVisibleIndex = 6, totalItems = 10, threshold = 3)).isTrue()
        assertThat(shouldLoadMore(lastVisibleIndex = 9, totalItems = 10, threshold = 3)).isTrue()
    }

    @Test
    fun `阈值为零时只在最后一项触发`() {
        assertThat(shouldLoadMore(lastVisibleIndex = 8, totalItems = 10, threshold = 0)).isFalse()
        assertThat(shouldLoadMore(lastVisibleIndex = 9, totalItems = 10, threshold = 0)).isTrue()
    }

    @Test
    fun `单条数据立即触发`() {
        assertThat(shouldLoadMore(lastVisibleIndex = 0, totalItems = 1)).isTrue()
    }

    @Test
    fun `负阈值不触发以免永不加载`() {
        assertThat(shouldLoadMore(lastVisibleIndex = 9, totalItems = 10, threshold = -1)).isFalse()
    }

    // ---- T-098：搜索结果摘要文案 ----

    @Test
    fun `摘要与原型文案逐字一致`() {
        // 原型 v-search-filter：搜索结果（3 条匹配“体脂”）
        assertThat(searchResultSummary(3, "体脂"))
            .isEqualTo("搜索结果（3 条匹配“体脂”）")
    }

    @Test
    fun `空查询不给摘要`() {
        assertThat(searchResultSummary(0, "")).isNull()
        assertThat(searchResultSummary(5, "   ")).isNull()
    }

    @Test
    fun `查询词首尾空白被去掉`() {
        assertThat(searchResultSummary(1, " 体重 "))
            .isEqualTo("搜索结果（1 条匹配“体重”）")
    }

    @Test
    fun `零命中仍是可见结论`() {
        assertThat(searchResultSummary(0, "体脂"))
            .isEqualTo("搜索结果（0 条匹配“体脂”）")
    }
}
