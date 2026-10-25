package com.xcwk.scalehub.feature.measure

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 测量模块交付屏的 Compose 预览集合。
 *
 * `MeasureScreen` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 结果分享流程 · 默认态。 */
@Preview(name = "结果分享 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ResultShareFlowPreviewDefault() {
    QkTheme {
        ResultShareFlow(state = ResultShareUiState(), onIntent = {})
    }
}

/** 结果分享路由 · 默认态。 */
@Preview(name = "结果分享路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ResultShareRoutePreviewDefault() {
    QkTheme {
        ResultShareRoute(onBack = {})
    }
}
