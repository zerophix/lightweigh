package com.xcwk.scalehub.feature.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/** 隐私授权流程的 Compose 预览集合。 */

/** 隐私授权流程 · 默认态。 */
@Preview(name = "隐私授权 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivacyAuthFlowPreviewDefault() {
    QkTheme {
        PrivacyAuthFlow(state = PrivacyAuthUiState(), onIntent = {})
    }
}
