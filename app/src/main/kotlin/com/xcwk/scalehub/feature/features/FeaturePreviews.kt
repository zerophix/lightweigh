package com.xcwk.scalehub.feature.features

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/** 未交付功能占位屏的 Compose 预览。 */
@Preview(name = "功能占位 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeatureScreenPreviewDefault() {
    QkTheme {
        FeatureScreen(name = "功能占位", onBack = {})
    }
}

/** 未交付功能占位路由。 */
@Preview(name = "功能占位路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeatureRoutePreviewDefault() {
    QkTheme {
        FeatureRoute(name = "功能占位", onBack = {})
    }
}
