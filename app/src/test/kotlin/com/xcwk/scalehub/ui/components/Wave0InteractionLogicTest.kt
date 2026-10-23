package com.xcwk.scalehub.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Wave 0 交互组件里不依赖 ViewModel 的规则。 */
class Wave0InteractionLogicTest {

    @Test
    fun 下拉相位按拖动比例与刷新中切换() {
        assertThat(pullRefreshPhase(0f, false)).isEqualTo(PullRefreshPhase.Idle)
        assertThat(pullRefreshPhase(0.4f, false)).isEqualTo(PullRefreshPhase.Pulling)
        assertThat(pullRefreshPhase(1f, false)).isEqualTo(PullRefreshPhase.Armed)
        assertThat(pullRefreshPhase(1.4f, true)).isEqualTo(PullRefreshPhase.Refreshing)
        assertThat(pullRefreshHint(PullRefreshPhase.Pulling)).isEqualTo("出现刷新图标")
        assertThat(pullRefreshHint(PullRefreshPhase.Armed)).isEqualTo("松手触发")
        assertThat(pullRefreshHint(PullRefreshPhase.Idle)).contains("列表顶部下拉")
    }

    @Test
    fun 左滑未过半收回_过半保持露出() {
        assertThat(clampSwipeOffset(-20f, 80f)).isEqualTo(-20f)
        assertThat(clampSwipeOffset(-120f, 80f)).isEqualTo(-80f)
        assertThat(clampSwipeOffset(10f, 80f)).isEqualTo(0f)
        assertThat(settleSwipeOffset(-30f, 80f)).isEqualTo(0f)
        assertThat(settleSwipeOffset(-40f, 80f)).isEqualTo(-80f)
        assertThat(settleSwipeOffset(-10f, 0f)).isEqualTo(0f)
        assertThat(QkSwipeDeleteBody).contains("删除后本地与云端同步移除")
        assertThat(QkSwipeDeleteBody).contains("天内可恢复")
        assertThat(QkSwipeDeleteTitle).isEqualTo("删除这条记录？")
    }

    @Test
    fun 滚到尾部才请求下一页_加载中或到底不再请求() {
        assertThat(shouldLoadNextPage(lastVisibleIndex = 1, itemCount = 10, isLoading = false, endReached = false)).isFalse()
        assertThat(shouldLoadNextPage(lastVisibleIndex = 8, itemCount = 10, isLoading = false, endReached = false)).isTrue()
        assertThat(shouldLoadNextPage(lastVisibleIndex = 9, itemCount = 10, isLoading = true, endReached = false)).isFalse()
        assertThat(shouldLoadNextPage(lastVisibleIndex = 9, itemCount = 10, isLoading = false, endReached = true)).isFalse()
        assertThat(shouldLoadNextPage(lastVisibleIndex = -1, itemCount = 10, isLoading = false, endReached = false)).isFalse()
        assertThat(nextLoadedCount(loaded = 20, total = 128, pageSize = 20)).isEqualTo(40)
        assertThat(nextLoadedCount(loaded = 120, total = 128, pageSize = 20)).isEqualTo(128)
        assertThat(infiniteScrollEndLabel(128)).isEqualTo("已加载全部 128 条")
        assertThat(infiniteScrollLoadingLabel()).contains("加载中")
    }

    @Test
    fun 搜索按关键字匹配_本周本月与自定义窗口分开() {
        val records = prototypeSearchRecords() + QkSearchRecord("体重 70.0 kg", "40 天前", daysAgo = 40)
        val fat = filterSearchRecords(records, "体脂", QkSearchFilter.All)
        assertThat(fat).hasSize(3)
        assertThat(searchResultCaption(fat.size, "体脂")).contains("条匹配")
        assertThat(searchResultCaption(fat.size, "体脂")).contains("体脂")
        assertThat(filterSearchRecords(records, "", QkSearchFilter.Week)).hasSize(3)
        assertThat(filterSearchRecords(records, "", QkSearchFilter.Month).map { it.daysAgo }).doesNotContain(40)
        assertThat(inSearchWindow(3, QkSearchFilter.Custom, custom = null)).isFalse()
        assertThat(inSearchWindow(3, QkSearchFilter.Custom, custom = 0..5)).isTrue()
        assertThat(QkSearchFilter.entries.map { it.label }).containsExactly("全部", "本周", "本月", "自定义").inOrder()
        assertThat(QkSearchPlaceholder).contains("搜索指标、日期、备注")
        assertThat(searchFilterFeedback(QkSearchFilter.Week)).isEqualTo("已筛选：本周")
        assertThat(searchFilterFeedback(QkSearchFilter.All)).isNull()
    }

    @Test
    fun 长按菜单三项与展开收起文案() {
        assertThat(QkLongPressAction.entries.map { it.label }).containsExactly("复制", "分享", "全选").inOrder()
        assertThat(longPressToggleLabel(false)).isEqualTo("展开")
        assertThat(longPressToggleLabel(true)).isEqualTo("收起")
        assertThat(QkLongPressCollapsed).contains("林悦")
        assertThat(QkLongPressCollapsed).contains("保持均衡饮食")
        assertThat(QkLongPressExpanded).startsWith("林悦")
        assertThat(QkLongPressHint).contains("选区高亮为品牌色")
        assertThat(QkLongPressBody).contains("体脂率")
    }

    @Test
    fun 骨架屏说明保持布局稳定() {
        assertThat(QkSkeletonTitle).isEqualTo("骨架屏加载")
        assertThat(QkSkeletonHint).contains("内容加载时保持布局稳定")
        assertThat(QkSkeletonHint).contains("避免跳动")
        assertThat(QkSkeletonBack).isEqualTo("返回组件演示")
        assertThat(QkSkeletonShape.entries).containsExactly(
            QkSkeletonShape.Line,
            QkSkeletonShape.Circle,
            QkSkeletonShape.Block,
        ).inOrder()
    }
}
