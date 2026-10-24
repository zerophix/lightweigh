package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * guest-mode — 访客模式确认页与访客落地页（T-072 · 原型01 v-guest / v-guest-done）。
 *
 * ## 呈现式约束
 * 本文件不创建任何本地账号、不写数据库：进入访客由 [GuestModeFlow] 的
 * `onEnterGuest()` 回调交给既有 auth/会话流程；「登录 / 注册」经
 * `onLoginOrRegister()` 回到登录主路径。
 *
 * ## 状态
 * 两态（[GuestStep]）之间的转移是纯函数 [guestModeReduce]，JVM 单测钉死；
 * 访客数据不落库这件事只作为警示文案呈现，不做真实校验。
 */

// ---- 两态状态机 ----

/** 访客流程的两个呈现态（原型 v-guest / v-guest-done）。 */
enum class GuestStep { Notice, Browsing }

/** 访客流程的呈现状态。 */
data class GuestUiState(
    val step: GuestStep = GuestStep.Notice,
)

/** 访客流程的意图。 */
sealed interface GuestIntent {
    /** 点击「进入体验」：切到落地页，真实会话建立走回调。 */
    data object EnterExperience : GuestIntent

    /** 点击「返回登录」：退回登录方式选择。 */
    data object BackToLogin : GuestIntent

    /** 落地页点击「登录 / 注册」：外部导航，呈现态不变。 */
    data object LoginOrRegister : GuestIntent

    /** 落地页点击「重新走一遍登录流程」：同「返回登录」。 */
    data object RestartLogin : GuestIntent
}

/** 纯呈现态转移。 */
fun guestModeReduce(state: GuestUiState, intent: GuestIntent): GuestUiState = when (intent) {
    GuestIntent.EnterExperience -> state.copy(step = GuestStep.Browsing)
    GuestIntent.BackToLogin, GuestIntent.RestartLogin -> state.copy(step = GuestStep.Notice)
    GuestIntent.LoginOrRegister -> state
}

// ---- 逐字文案（原型 01-login.html） ----

/** 访客确认页文案（v-guest）。 */
object GuestModeText {
    const val Headline = "先逛逛，不用登录"
    const val Subline = "访客模式下数据仅保存在本机，更换设备将丢失"
    const val LimitLocalOnly = "数据仅存本机，卸载或换设备将丢失"
    const val LimitNoSync = "无法云同步，无法多设备查看"
    const val Enter = "进入体验"
    const val BackToLogin = "返回登录"
}

/** 访客落地页文案（v-guest-done）。 */
object GuestLandingText {
    const val Brand = "轻衡"
    const val Tag = "访客"
    const val Headline = "你正在以访客身份浏览"
    const val Subline = "登录后可同步数据、连接设备、使用 AI 健康助手。"
    const val LoginOrRegister = "登录 / 注册"
    const val RestartLogin = "← 重新走一遍登录流程"
}

// ---- 页面 ----

/** 访客模式两条限制（原型 .limit 行：信息图标 + 文案）。 */
@Composable
private fun GuestLimitRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(Icons.Outlined.Info, tint = QkColor.Muted, size = QkSpacing.Lg)
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** 访客确认页（原型 v-guest）。 */
@Composable
private fun GuestNoticeView(
    onEnterGuest: () -> Unit,
    onBackToLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            QkBackButton(onClick = onBack)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Tile))
                    .background(QkColor.Mint)
                    .padding(QkSpacing.Lg),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(
                    Icons.Outlined.Eco,
                    tint = QkColor.Primary,
                    size = QkSpacing.Xl + QkSpacing.Md,
                )
            }
            Text(
                GuestModeText.Headline,
                modifier = Modifier.padding(top = QkSpacing.Xl),
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                GuestModeText.Subline,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            GuestLimitRow(GuestModeText.LimitLocalOnly)
            GuestLimitRow(GuestModeText.LimitNoSync)
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = GuestModeText.Enter,
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnterGuest,
            )
            QkButton(
                text = GuestModeText.BackToLogin,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = onBackToLogin,
            )
        }
    }
}

/** 访客落地页（原型 v-guest-done）：品牌行 + 说明卡 + 登录入口。 */
@Composable
private fun GuestLandingView(
    onLoginOrRegister: () -> Unit,
    onRestartLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Field))
                    .background(QkColor.Mint)
                    .padding(QkSpacing.Sm),
            ) {
                QkIcon(Icons.Outlined.Eco, tint = QkColor.Primary, size = QkSpacing.Lg)
            }
            Text(
                GuestLandingText.Brand,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            QkPill(text = GuestLandingText.Tag, tone = QkPillTone.Gray)
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl), padding = QkSpacing.Xl) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    GuestLandingText.Headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Fg,
                    textAlign = TextAlign.Center,
                )
                Text(
                    GuestLandingText.Subline,
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = GuestLandingText.LoginOrRegister,
                modifier = Modifier.fillMaxWidth(),
                onClick = onLoginOrRegister,
            )
            Text(
                GuestLandingText.RestartLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRestartLogin)
                    .padding(vertical = QkSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.GhostInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 访客流程路由：按 [GuestUiState.step] 呈现确认页或落地页。
 *
 * 外部交接点：
 * - [onEnterGuest] 确认进入访客模式（真实会话由既有流程建立）
 * - [onBackToLogin] 返回登录方式选择
 * - [onLoginOrRegister] 落地页的登录 / 注册入口
 */
@Composable
fun GuestModeFlow(
    state: GuestUiState,
    onIntent: (GuestIntent) -> Unit,
    onEnterGuest: () -> Unit,
    onBackToLogin: () -> Unit,
    onLoginOrRegister: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onBackToLogin,
) {
    when (state.step) {
        GuestStep.Notice -> GuestNoticeView(
            onEnterGuest = {
                onIntent(GuestIntent.EnterExperience)
                onEnterGuest()
            },
            onBackToLogin = {
                onIntent(GuestIntent.BackToLogin)
                onBackToLogin()
            },
            onBack = onBack,
            modifier = modifier,
        )

        GuestStep.Browsing -> GuestLandingView(
            onLoginOrRegister = {
                onIntent(GuestIntent.LoginOrRegister)
                onLoginOrRegister()
            },
            onRestartLogin = {
                onIntent(GuestIntent.RestartLogin)
                onBackToLogin()
            },
            modifier = modifier,
        )
    }
}
