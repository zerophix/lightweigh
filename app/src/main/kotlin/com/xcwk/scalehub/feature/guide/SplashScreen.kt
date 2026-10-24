package com.xcwk.scalehub.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * guide — 启动边界前两屏：闪屏 + 条款确认（T-075 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/02-onboarding-permission.html`：
 * - `v-splash` 闪屏：品牌块 + 「轻衡」+ 「让健康，轻一点。」
 * - `v-terms` 条款页：两个勾选（用户协议 / 隐私政策）+ 不收集横幅 + 同意/退出
 *
 * 权限说明与授权结果在 `PermissionFlow.kt`（同包，同一启动边界的后续三屏）。
 *
 * ## 验收（AC-V5 权限用时申请）
 * - 闪屏与条款页文案逐字取自原型，常量见本文件顶部，JVM 单测钉死
 * - 未全部勾选时「同意并继续」不可用（[TermsUiState.acceptEnabled]，纯函数）
 * - 未同意条款前不得进入权限说明：门控是 [canProceedToPermissions]
 *
 * ## 边界（呈现式组件）
 * 本文件不建 ViewModel、不读写存储、不碰系统权限 API：
 * 勾选与阶段推进只改内存态（[SplashUiState.reduce]），
 * 「同意后去哪」「退出应用」由宿主经 [TermsScreen] 的 onAccepted / onDeclined 决定。
 */

// ---- 静态文案（逐字取自原型 v-splash / v-terms，JVM 单测校验） ----

// v-splash
const val SPLASH_TITLE = "轻衡"
const val SPLASH_TAGLINE = "让健康，轻一点。"
const val SPLASH_ENTER_HINT = "点击进入 ›"

// v-terms
const val TERMS_HEADLINE = "你的健康数据，由你做主"
const val TERMS_SUBLINE = "开始前，请阅读并同意以下条款。"
const val TERMS_AGREEMENT_LABEL = "我已阅读并同意《用户协议》"
const val TERMS_PRIVACY_LABEL = "我已阅读并同意《隐私政策》"
const val TERMS_BANNER = "未同意前，我们不会收集任何个人数据。"
const val TERMS_ACCEPT = "同意并继续"
const val TERMS_DECLINE = "不同意，退出应用"

// ---- 条款勾选状态机（纯函数，JVM 单测钉死） ----

/**
 * 条款勾选快照。
 *
 * 两个勾选是**各自独立**的：用户可以只同意其一，但这不构成进入下一步的条件。
 * [acceptEnabled] 是「同意并继续」按钮的可用性口径，也是权限申请的前置门控。
 */
data class TermsUiState(
    val agreementAgreed: Boolean = false,
    val privacyAgreed: Boolean = false,
) {
    /** 两项均已勾选才允许继续（AC：未全部勾选时「同意并继续」不可用）。 */
    val acceptEnabled: Boolean get() = agreementAgreed && privacyAgreed
}

/** 条款页的勾选意图（只改内存态，不落库、不写偏好）。 */
sealed interface TermsIntent {
    data object ToggleAgreement : TermsIntent
    data object TogglePrivacy : TermsIntent
}

/** 纯状态转移：每个意图只翻转自己的勾选，互不影响。 */
fun TermsUiState.reduce(intent: TermsIntent): TermsUiState = when (intent) {
    TermsIntent.ToggleAgreement -> copy(agreementAgreed = !agreementAgreed)
    TermsIntent.TogglePrivacy -> copy(privacyAgreed = !privacyAgreed)
}

/**
 * 启动边界门控：闪屏之后才到条款页，且条款全勾选才允许发起权限说明。
 *
 * 抽成纯函数是「未同意条款前不请求任何权限」这条 forbidden 的可执行证据：
 * 宿主在跳转权限流程前调用它即可，不必依赖按钮视觉态。
 */
fun canProceedToPermissions(step: SplashStep, terms: TermsUiState): Boolean =
    step == SplashStep.Terms && terms.acceptEnabled

/** 启动边界的前两态（与原型 v-splash / v-terms 一一对应）。 */
enum class SplashStep { Splash, Terms }

/** 启动边界前两屏的呈现态。 */
data class SplashUiState(
    val step: SplashStep = SplashStep.Splash,
    val terms: TermsUiState = TermsUiState(),
)

/** 启动边界前两屏的呈现态转移；真正跳转由宿主结合 [canProceedToPermissions] 决定。 */
sealed interface SplashIntent {
    data object Enter : SplashIntent
    data class ToggleAgreement(val intent: TermsIntent) : SplashIntent
}

/** 纯状态转移：进入条款页只切 stage，勾选转交 [TermsUiState.reduce]。 */
fun SplashUiState.reduce(intent: SplashIntent): SplashUiState = when (intent) {
    SplashIntent.Enter -> copy(step = SplashStep.Terms)
    is SplashIntent.ToggleAgreement -> copy(terms = terms.reduce(intent.intent))
}

// ---- 呈现层 ----

/**
 * v-splash 闪屏。
 *
 * @param logoSize 品牌块边长。原型为 88px，设计 token 里没有这一档，
 *   故按「尺寸无 token 时作为 Dp 参数暴露」的约定默认取最近的大尺寸 token，调用方可覆盖。
 */
@Composable
fun SplashScreen(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    logoSize: Dp = QkComponentSize.SkeletonBlock,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(logoSize)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Primary),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Device, tint = QkColor.Card, size = QkSpacing.Xl)
        }
        Text(
            text = SPLASH_TITLE,
            modifier = Modifier.padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.displayMedium,
            color = QkColor.Fg,
        )
        Text(
            text = SPLASH_TAGLINE,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = SPLASH_ENTER_HINT,
            modifier = Modifier
                .clickable(onClick = onEnter)
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Xl),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Primary,
        )
    }
}

/**
 * v-terms 条款确认页。
 *
 * @param onAccepted 两项均勾选后点「同意并继续」。按钮在未全勾选时不可用，
 *   回调只在可用态触发（门控由 [TermsUiState.acceptEnabled] 决定）。
 * @param onDeclined 「不同意，退出应用」：退出动作归宿主（本组件不调用任何系统 API）。
 */
@Composable
fun TermsScreen(
    state: TermsUiState,
    onIntent: (TermsIntent) -> Unit,
    modifier: Modifier = Modifier,
    onAccepted: () -> Unit = {},
    onDeclined: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Xl))

        // hero：圆底图标 + 主标题 + 副标题（原型 .hero.center）
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(QkComponentSize.SkeletonBlock)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Privacy, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = TERMS_HEADLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = TERMS_SUBLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkCard {
            TermsCheckRow(
                checked = state.agreementAgreed,
                label = TERMS_AGREEMENT_LABEL,
                onToggle = { onIntent(TermsIntent.ToggleAgreement) },
            )
            TermsCheckRow(
                checked = state.privacyAgreed,
                label = TERMS_PRIVACY_LABEL,
                onToggle = { onIntent(TermsIntent.TogglePrivacy) },
            )
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(QkRadius.Button))
                .background(QkColor.Mint)
                .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkIcon(QkIcons.Privacy, tint = QkColor.Primary)
            Text(
                text = TERMS_BANNER,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = TERMS_ACCEPT,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.acceptEnabled,
            onClick = onAccepted,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = TERMS_DECLINE,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onDeclined,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 条款勾选行（原型 .row .chk：方形勾选框 + 单行文案，整行可点）。 */
@Composable
private fun TermsCheckRow(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        val shape = RoundedCornerShape(QkRadius.Field)
        Box(
            modifier = Modifier
                .size(QkSpacing.Xl)
                .clip(shape)
                .background(if (checked) QkColor.Primary else QkColor.Card)
                .border(QkStroke.Thin, if (checked) QkColor.Primary else QkColor.Border, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Card,
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
    }
}
