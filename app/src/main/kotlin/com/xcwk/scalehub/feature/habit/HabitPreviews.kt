package com.xcwk.scalehub.feature.habit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 习惯模块交付屏的 Compose 预览。
 *
 * `HabitRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 习惯列表屏 · 默认态。 */
@Preview(name = "习惯列表 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HabitScreenPreviewDefault() {
    QkTheme {
        HabitScreen()
    }
}
