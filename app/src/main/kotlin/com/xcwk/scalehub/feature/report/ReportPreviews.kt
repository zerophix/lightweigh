package com.xcwk.scalehub.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 报告模块交付屏的 Compose 预览。
 *
 * `ReportRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 报告页 · 默认态。 */
@Preview(name = "报告 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ReportScreenPreviewDefault() {
    QkTheme {
        ReportScreen(state = ReportUiState())
    }
}
