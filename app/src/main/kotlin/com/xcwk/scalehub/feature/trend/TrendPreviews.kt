package com.xcwk.scalehub.feature.trend

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 趋势与指标模块交付屏的 Compose 预览集合。
 *
 * `TrendRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 趋势页 · 默认态。 */
@Preview(name = "趋势 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TrendScreenPreviewDefault() {
    QkTheme {
        TrendScreen(state = TrendUiState(), onIntent = {})
    }
}

/** 指标列表 · 八个指标。 */
@Preview(name = "指标列表 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MetricListScreenPreviewDefault() {
    QkTheme {
        MetricListScreen(readings = METRIC_SPECS.map { MetricReading(spec = it, value = "--") })
    }
}

/** 指标列表 · 空态。 */
@Preview(name = "指标列表 · 空态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MetricListScreenPreviewEmpty() {
    QkTheme {
        MetricListScreen(readings = emptyList())
    }
}

/** 指标详情 · 体重。 */
@Preview(name = "指标详情 · 体重", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MetricDetailScreenPreviewDefault() {
    QkTheme {
        MetricDetailScreen(
            reading = MetricReading(
                spec = METRIC_SPECS.first(),
                value = "62.4",
                deltaText = "-0.3",
            ),
        )
    }
}
