package com.xcwk.scalehub.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 数据录入模块交付屏的 Compose 预览。
 *
 * `RecordRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 录入页 · 默认态。 */
@Preview(name = "录入 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RecordScreenPreviewDefault() {
    QkTheme {
        RecordScreen(state = RecordUiState(), onIntent = {})
    }
}
