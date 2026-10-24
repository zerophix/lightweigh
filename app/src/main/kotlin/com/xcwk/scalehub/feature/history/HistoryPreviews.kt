package com.xcwk.scalehub.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 历史记录模块交付屏的 Compose 预览集合。
 *
 * `HistoryRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

private val previewDetailUi = HistoryDetailUi(
    recordId = 1L,
    metaText = "林悦 · 10月21日 07:30 · 已同步",
    weightValue = "62.4",
    weightDelta = "-0.3",
    fatRateValue = "28.6",
    fatRateDelta = "-0.2",
    muscleValue = "42.3",
    muscleDelta = "+0.1",
    scoreValue = "82",
)

private val previewEditModel = RecordEditModel(
    recordId = 1L,
    timeText = "10月21日 07:30",
    note = "晨起空腹测量",
    weightText = "62.4",
    fatRateText = "28.6",
    muscleText = "42.3",
    bmiText = "22.1",
)

/** 历史列表 · 默认态。 */
@Preview(name = "历史列表 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryScreenPreviewDefault() {
    QkTheme {
        HistoryScreen(state = HistoryUiState(), onIntent = {})
    }
}

/** 历史列表 · 空态。 */
@Preview(name = "历史列表 · 空态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryScreenPreviewEmpty() {
    QkTheme {
        HistoryScreen(state = HistoryUiState(loading = false, emptyText = "还没有测量记录"), onIntent = {})
    }
}

/** 历史列表 · 错误态。 */
@Preview(name = "历史列表 · 错误态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryScreenPreviewError() {
    QkTheme {
        HistoryScreen(state = HistoryUiState(loading = false, error = "数据读取失败，请重试"), onIntent = {})
    }
}

/** 测量详情 · 已同步态。 */
@Preview(name = "测量详情 · 已同步", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryDetailScreenPreviewDefault() {
    QkTheme {
        HistoryDetailScreen(detail = previewDetailUi)
    }
}

/** 测量详情 · 本地未同步态。 */
@Preview(name = "测量详情 · 未同步", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryDetailScreenPreviewUnsynced() {
    QkTheme {
        HistoryDetailScreen(detail = previewDetailUi.copy(synced = false))
    }
}

/** 记录编辑页 · 默认态。 */
@Preview(name = "记录编辑 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RecordEditScreenPreviewDefault() {
    QkTheme {
        RecordEditScreen(model = previewEditModel)
    }
}

/** 记录编辑页 · 校验失败态。 */
@Preview(name = "记录编辑 · 校验失败", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RecordEditScreenPreviewViolation() {
    QkTheme {
        RecordEditScreen(
            model = previewEditModel,
            saveViolations = listOf("体重来自设备，不可修改"),
        )
    }
}

/** 历史对比页 · 两行对比。 */
@Preview(name = "历史对比 · 两条记录", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryCompareScreenPreviewDefault() {
    QkTheme {
        HistoryCompareScreen(
            rows = listOf(
                HistoryRow(time = 1L, timeText = "10-21 07:30", weightText = "62.4", fatText = "28.6%"),
                HistoryRow(time = 2L, timeText = "10-20 07:20", weightText = "62.7", fatText = "28.8%"),
            ),
        )
    }
}

/** 历史对比页 · 空态。 */
@Preview(name = "历史对比 · 空态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HistoryCompareScreenPreviewEmpty() {
    QkTheme {
        HistoryCompareScreen()
    }
}
