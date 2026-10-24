package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * sdk-list — 第三方 SDK 清单页（T-092 · 原型26 v-sdk-list）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/26-final-gaps.html` id="v-sdk-list"
 * - 验收：AC-S5「SDK 清单与数据恢复」，页内文案逐字取自原型，禁止改写
 *
 * ## 呈现式约束（AC-S5 forbidden，硬约束）
 * 1. SDK 清单数据由调用方注入（[SdkListUiState.entries]），本文件不读包管理器、
 *    不发网络请求——清单内容与隐私政策链接的获取属于宿主职责；
 * 2. 同意开关状态同样外部注入，用户点按只经 [SdkListScreen] 的
 *    onToggleSdkConsent / onOpenSdkPolicy 回传，页面不自建状态；
 * 3. 颜色/间距一律 Qk token，列表行复用 [QkListRow]。
 */

/** 原型 v-sdk-list 文案（逐字，勿改）。 */
object SdkListText {
    const val TopBarTitle = "第三方 SDK"
    const val BannerLine1 = "以下第三方 SDK 用于实现对应功能，"
    const val BannerLine2 = "我们要求其遵守本隐私政策。"
    const val PurposePrefix = "用途："
    const val CollectedPrefix = "收集："
    const val FooterNote = "点击可查看各 SDK 的隐私政策链接与数据处理说明。"
    const val BackAction = "返回"
}

/**
 * 单个第三方 SDK 条目。
 *
 * [consentToggleable] 对应原型「收集：设备标识（可关闭）」的 JPush 行——只有这类
 * 条目渲染开关；[consentGranted] 由外部注入，页面只呈现不落盘。
 */
data class SdkEntry(
    val id: String,
    val name: String,
    val purpose: String,
    val collected: String,
    val policyUrl: String = "",
    val consentToggleable: Boolean = false,
    val consentGranted: Boolean = true,
) {
    /** 原型 `用途：…` 行（逐字拼接，前缀取自 [SdkListText.PurposePrefix]）。 */
    val purposeLine: String get() = SdkListText.PurposePrefix + purpose

    /** 原型 `收集：…` 行。 */
    val collectedLine: String get() = SdkListText.CollectedPrefix + collected
}

/**
 * 原型 v-sdk-list 的默认清单（4 条，顺序与原型一致）。
 *
 * 仅作为「宿主未注入时的等价快照」存在；真实清单必须由调用方注入，
 * 页面不自行扫描依赖。
 */
fun defaultSdkEntries(): List<SdkEntry> = listOf(
    SdkEntry(
        id = "wechat-opensdk",
        name = "微信 OpenSDK",
        purpose = "微信登录与分享",
        collected = "openid、昵称、头像",
    ),
    SdkEntry(
        id = "jpush",
        name = "极光推送 JPush",
        purpose = "测量提醒通知",
        collected = "设备标识（可关闭）",
        consentToggleable = true,
        consentGranted = true,
    ),
    SdkEntry(
        id = "aliyun-oss",
        name = "阿里云 OSS",
        purpose = "健康数据加密云存储",
        collected = "加密后的测量数据",
    ),
    SdkEntry(
        id = "glide",
        name = "Glide",
        purpose = "图片加载",
        collected = "无个人数据",
    ),
)

/** SDK 清单页状态：清单与开关值全部外部注入。 */
data class SdkListUiState(
    val entries: List<SdkEntry> = defaultSdkEntries(),
    val topBarTitle: String = SdkListText.TopBarTitle,
)

/**
 * 纯状态转移：把某条 SDK 的同意开关改到 [granted]。
 *
 * 幂等——目标值与现值相同、id 不存在、条目不可关闭时原样返回，不产生新副本；
 * 宿主可据此判等跳过重复写入。
 */
fun SdkListUiState.withSdkConsent(id: String, granted: Boolean): SdkListUiState {
    val index = entries.indexOfFirst { it.id == id }
    if (index < 0) return this
    val entry = entries[index]
    if (!entry.consentToggleable || entry.consentGranted == granted) return this
    val updated = entries.toMutableList().also { it[index] = entry.copy(consentGranted = granted) }
    return copy(entries = updated)
}

/** 第三方 SDK 清单页（原型 v-sdk-list）。纯呈现，清单与回调全部来自调用方。 */
@Composable
fun SdkListScreen(
    state: SdkListUiState = SdkListUiState(),
    onToggleSdkConsent: (String, Boolean) -> Unit = { _, _ -> },
    onOpenSdkPolicy: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = state.topBarTitle,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            QkNoticeBanner(
                icon = Icons.Outlined.Info,
                tone = QkGlyphTone.Green,
                background = QkColor.MintSoft,
            ) {
                Text(
                    text = SdkListText.BannerLine1,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
                Text(
                    text = SdkListText.BannerLine2,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }

            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                state.entries.forEachIndexed { index, entry ->
                    QkListRow(
                        title = entry.name,
                        subtitle = "${entry.purposeLine}\n${entry.collectedLine}",
                        showDivider = index != state.entries.lastIndex,
                        onClick = { onOpenSdkPolicy(entry.id) },
                        trailing = {
                            if (entry.consentToggleable) {
                                Switch(
                                    checked = entry.consentGranted,
                                    onCheckedChange = { onToggleSdkConsent(entry.id, it) },
                                )
                            } else {
                                QkIcon(icon = QkIcons.ChevronRight, tint = QkColor.Hint)
                            }
                        },
                    )
                }
            }

            Text(
                text = SdkListText.FooterNote,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(QkSpacing.Xl))

            QkButton(
                text = SdkListText.BackAction,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = onBack,
            )
            Spacer(Modifier.height(QkSpacing.Xl))
        }
    }
}

/**
 * 原型 `.banner` 版式：图标泡 + 多行说明（INFO/警示共用）。
 *
 * 放在本文件是 T-092 的建文件约束所致：本任务只允许新建
 * SdkListScreen.kt / DataRecovery.kt / MaintenanceScreen.kt 三个文件，
 * DataRecovery.kt 的覆盖警示复用本版式。
 */
@Composable
internal fun QkNoticeBanner(
    icon: ImageVector,
    tone: QkGlyphTone,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(background)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIconBubble(
            icon = icon,
            tone = tone,
            size = QkSpacing.Xl * 2,
        )
        Column(modifier = Modifier.weight(1f), content = content)
    }
}
