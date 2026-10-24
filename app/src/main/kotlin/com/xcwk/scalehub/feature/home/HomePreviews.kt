package com.xcwk.scalehub.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/** 首页各状态的 Compose 预览集合。 */

/** 首页 · 默认态。 */
@Preview(name = "首页 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeStateScreenPreviewDefault() {
    QkTheme {
        HomeStateScreen(state = HomeUiState(), onEvent = {})
    }
}

/** 首页状态路由 · 默认态。 */
@Preview(name = "首页状态路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeStatesRoutePreviewDefault() {
    QkTheme {
        HomeStatesRoute(initial = HomeUiState(), onClose = {})
    }
}
