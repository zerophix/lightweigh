package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkVSpace
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * mine — 关于页（原型07 v-about）。
 *
 * 版本号、协议链接与 SDK 清单入口来自传入参数（AC 末条），界面不写死业务数据；
 * 静态标题/版权行逐字取自原型。
 */

// ---- 静态文案（逐字取自原型 07 v-about） ----

const val TITLE_ABOUT = "关于轻衡"
const val APP_NAME_ABOUT = "轻衡"
const val ABOUT_TAGLINE_TEMPLATE = "%s · 让健康轻一点"
const val ROW_USER_AGREEMENT = "用户协议"
const val ROW_PRIVACY_POLICY = "隐私政策"
const val ROW_SDK_LIST = "第三方 SDK 清单"
const val ABOUT_COPYRIGHT = "© 2026 轻衡 · MIT License"

/** 副标题：`v0.1.0 · 让健康轻一点`（版本号由调用方注入）。 */
fun aboutTagline(version: String): String = ABOUT_TAGLINE_TEMPLATE.format(version)

/** 关于页三个条目（顺序即原型顺序）。 */
val ABOUT_ROWS = listOf(ROW_USER_AGREEMENT, ROW_PRIVACY_POLICY, ROW_SDK_LIST)

/**
 * 关于页（无状态）：应用名 + 副标题 + 三个条目 + 版权行。
 * 协议/清单入口点击回调由调用方注入（[onRowClick] 收条目名）。
 */
@Composable
fun AboutScreen(
    version: String,
    onRowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkVSpace(QkSpacing.Lg)
        Text(APP_NAME_ABOUT, style = MaterialTheme.typography.headlineMedium)
        QkVSpace(QkSpacing.Xs)
        Text(
            aboutTagline(version),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Muted,
        )
        QkVSpace(QkSpacing.Lg)
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                ABOUT_ROWS.forEach { row ->
                    QkListRow(
                        title = row,
                        modifier = Modifier.fillMaxWidth().clickable { onRowClick(row) },
                    )
                }
            }
        }
        QkVSpace(QkSpacing.Lg)
        Text(
            ABOUT_COPYRIGHT,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
    }
}
