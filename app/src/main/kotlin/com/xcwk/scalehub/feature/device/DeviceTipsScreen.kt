package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/** 设备页「测量小贴士」入口行文案（T-053）。 */
const val DEVICE_TIPS_ENTRY_TITLE = "测量小贴士"

/** 入口行副标题：说清点进去能看到什么。 */
const val DEVICE_TIPS_ENTRY_HINT = "正确站姿、测量时机，以及最近一次测量时间"

/** 小贴士页标题。 */
const val DEVICE_TIPS_TITLE = "测量小贴士"

/** 无最近测量时的占位文案（组件内同文案，这里暴露给单测逐字断言）。 */
const val DEVICE_TIPS_NO_RECENT = "暂无记录"

/**
 * 最近测量时间文案：空白串按「无记录」处理，不编造时间。
 *
 * 纯函数，JVM 单测可断言——设备页当前只持有扫描/绑定态，没有测量时间来源，
 * 因此宿主传入 null，页面如实显示 [DEVICE_TIPS_NO_RECENT]。
 */
fun deviceRecentTimeText(measuredAtText: String?): String? =
    measuredAtText?.takeIf { it.isNotBlank() }

/**
 * device-tips 宿主页（T-053）：设备页 →「测量小贴士」→ 本页 → 返回。
 *
 * 只做版式与返回路径，贴士正文与「开始称重」按钮都在既有
 * [DeviceTipsCard] / [DeviceRecentCard] 内（本页不复制文案）。
 */
@Composable
fun DeviceTipsScreen(
    recentTimeText: String? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onStartMeasure: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(modifier = Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(QkSpacing.Sm))
            Text(
                text = DEVICE_TIPS_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.height(QkSpacing.Lg))
        DeviceTipsCard()

        Spacer(modifier = Modifier.height(QkSpacing.CardGap))
        DeviceRecentCard(
            recentTimeText = deviceRecentTimeText(recentTimeText),
            onStartMeasure = onStartMeasure,
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

@Preview(name = "测量小贴士 · 无记录", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceTipsScreenPreviewEmpty() {
    QkTheme {
        DeviceTipsScreen()
    }
}

@Preview(name = "测量小贴士 · 有记录", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceTipsScreenPreviewRecent() {
    QkTheme {
        DeviceTipsScreen(recentTimeText = "今天 07:30")
    }
}
