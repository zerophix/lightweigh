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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * account-lifecycle — 账号与启动边界七屏（T-074 · 原型14）。
 *
 * ## 覆盖的原型 view
 * - `v-forceupdate` 强制更新（仅「立即更新」，**无跳过**）
 * - `v-minver` 系统版本过低
 * - `v-privacy-lock` 未同意隐私政策的功能锁
 * - `v-codeerr` 验证码错误（剩余次数 + 重发倒计时）
 * - `v-expired` 登录过期
 * - `v-multidevice` 账号在其他设备登录
 * - `v-healthinfo` 健康数据使用说明（五问五答）
 *
 * `v-hub` 是原型的演示枢纽，不属于产品页面，不实现。
 *
 * ## 呈现式约束
 * 版本检查、清会话、改密码、跳应用商店等动作全部经回调交既有流程；本文件只做
 * 「事件 → 页面」分发（[accountBoundarySpec]）+ 逐字呈现。强更页刻意没有任何
 * 关闭/稍后入口：这是验收硬条件，由单测钉死。
 *
 * ## 文案
 * 全部集中在 [AccountLifecycleText] / [HealthInfoText]，逐字取自
 * `design/prototypes/interactive/14-account-lifecycle.html`。
 */

// ---- 事件 → 页面分发 ----

/** 系统抛出的账号/启动边界事件。 */
enum class AccountBoundaryEvent {
    ForceUpdate,
    MinVersion,
    PrivacyLock,
    CodeError,
    LoginExpired,
    MultiDevice,
    HealthInfo,
}

/** 事件对应的呈现页（与原型 view 一一对应）。 */
enum class AccountBoundaryView {
    ForceUpdate,
    MinVersion,
    PrivacyLock,
    CodeError,
    LoginExpired,
    MultiDevice,
    HealthInfo,
}

/**
 * 一个边界事件的呈现规格。
 *
 * @param dismissible 是否允许关闭；强更为 false（无「稍后」）
 * @param primaryAction 主按钮文案
 * @param secondaryAction 次按钮文案；null 表示没有次入口
 * @param isDialog 以弹层（true）还是整页（false）呈现
 */
data class AccountBoundarySpec(
    val view: AccountBoundaryView,
    val dismissible: Boolean,
    val primaryAction: String,
    val secondaryAction: String?,
    val isDialog: Boolean,
)

/**
 * 纯函数：边界事件 → 页面规格。
 *
 * 强更（[AccountBoundaryEvent.ForceUpdate]）的 `dismissible = false` 且
 * `secondaryAction = null` —— 不给关闭入口，也不给「稍后」。
 */
fun accountBoundarySpec(event: AccountBoundaryEvent): AccountBoundarySpec = when (event) {
    AccountBoundaryEvent.ForceUpdate -> AccountBoundarySpec(
        view = AccountBoundaryView.ForceUpdate,
        dismissible = false,
        primaryAction = AccountLifecycleText.ForceUpdateAction,
        secondaryAction = null,
        isDialog = true,
    )

    AccountBoundaryEvent.MinVersion -> AccountBoundarySpec(
        view = AccountBoundaryView.MinVersion,
        dismissible = true,
        primaryAction = AccountLifecycleText.MinVersionUpgrade,
        secondaryAction = AccountLifecycleText.MinVersionBack,
        isDialog = false,
    )

    AccountBoundaryEvent.PrivacyLock -> AccountBoundarySpec(
        view = AccountBoundaryView.PrivacyLock,
        dismissible = true,
        primaryAction = AccountLifecycleText.PrivacyLockAction,
        secondaryAction = null,
        isDialog = false,
    )

    AccountBoundaryEvent.CodeError -> AccountBoundarySpec(
        view = AccountBoundaryView.CodeError,
        dismissible = true,
        primaryAction = AccountLifecycleText.CodeLogin,
        secondaryAction = AccountLifecycleText.CodeBack,
        isDialog = false,
    )

    AccountBoundaryEvent.LoginExpired -> AccountBoundarySpec(
        view = AccountBoundaryView.LoginExpired,
        dismissible = true,
        primaryAction = AccountLifecycleText.ExpiredRelogin,
        secondaryAction = AccountLifecycleText.ExpiredLater,
        isDialog = true,
    )

    AccountBoundaryEvent.MultiDevice -> AccountBoundarySpec(
        view = AccountBoundaryView.MultiDevice,
        dismissible = true,
        primaryAction = AccountLifecycleText.MultiDeviceChangePassword,
        secondaryAction = AccountLifecycleText.MultiDeviceMine,
        isDialog = true,
    )

    AccountBoundaryEvent.HealthInfo -> AccountBoundarySpec(
        view = AccountBoundaryView.HealthInfo,
        dismissible = true,
        primaryAction = AccountLifecycleText.HealthInfoAcknowledge,
        secondaryAction = null,
        isDialog = false,
    )
}

/** 该事件是否允许跳过/关闭。强更恒为 false（单测断言用）。 */
fun accountBoundaryAllowsSkip(event: AccountBoundaryEvent): Boolean =
    accountBoundarySpec(event).dismissible

// ---- 逐字文案（原型 14-account-lifecycle.html） ----

/** 七屏文案。 */
object AccountLifecycleText {
    // v-forceupdate
    const val ForceUpdateTitle = "需要更新到新版本"
    const val ForceUpdateAction = "立即更新"

    // v-minver
    const val MinVersionTitle = "系统版本过低"
    const val MinVersionBody = "轻衡需要 Android 8.0 及以上，当前系统版本过低。"
    const val MinVersionUpgrade = "查看升级方法"
    const val MinVersionBack = "返回"

    // v-privacy-lock
    const val PrivacyLockNavTitle = "设备管理"
    const val PrivacyLockBanner = "需先同意《隐私政策》才能使用设备与数据功能。"
    const val PrivacyLockTitle = "功能暂不可用"
    const val PrivacyLockBody = "完成隐私政策确认后才能连接设备、保存数据。"
    const val PrivacyLockAction = "去同意隐私政策"

    // v-codeerr
    const val CodeNavTitle = "手机号登录"
    const val CodeHeadline = "输入验证码"
    const val CodeSentPrefix = "验证码已发送至 "
    const val CodeLabel = "验证码"
    const val CodeHelp = "收不到验证码？"
    const val CodeLockBanner = "5 次错误后需等待 10 分钟再试。"
    const val CodeLogin = "登录"
    const val CodeBack = "← 返回"

    // v-expired
    const val ExpiredTitle = "登录已过期"
    const val ExpiredBody = "为保护数据安全，登录状态已失效。重新登录后继续同步，本地记录不丢失。"
    const val ExpiredLater = "稍后"
    const val ExpiredRelogin = "重新登录"

    // v-multidevice
    const val MultiDeviceTitle = "账号已在其他设备登录"
    const val MultiDeviceMine = "是我的"
    const val MultiDeviceChangePassword = "不是我，改密码"

    // v-healthinfo
    const val HealthInfoNavTitle = "健康数据使用说明"
    const val HealthInfoBanner = "你的健康数据加密存储，不会用于广告或对外共享。"
    const val HealthInfoNote = "健康数据属敏感个人信息，详见《隐私政策》。"
    const val HealthInfoAcknowledge = "我知道了"
}

/** 强更版本对：当前版本与要求版本。 */
data class AppVersionPair(
    val current: String = "v0.1.0",
    val required: String = "v0.2.0",
)

/** 强更说明文案（版本号由宿主注入，不写死）。 */
fun forceUpdateMessage(
    pair: AppVersionPair = AppVersionPair(),
): String = "当前 ${pair.current} 已停止服务，需更新至 ${pair.required}（不可跳过）。"

/** 验证码最大尝试次数。 */
const val CODE_MAX_ATTEMPTS = 5

/** 超过最大尝试次数后的冷却时长（分钟）。 */
const val CODE_LOCK_MINUTES = 10

/** 一次失败后剩余可尝试次数（失败次数入口为「已失败次数」）。 */
fun remainingCodeAttempts(attemptsUsed: Int): Int =
    (CODE_MAX_ATTEMPTS - attemptsUsed).coerceAtLeast(0)

/** 是否已被冷却锁定。 */
fun codeLocked(attemptsUsed: Int): Boolean = attemptsUsed >= CODE_MAX_ATTEMPTS

/** 验证码错误提示：`验证码错误，还可尝试 4 次`。 */
fun codeErrorRemainingText(remainingAttempts: Int): String =
    "验证码错误，还可尝试 $remainingAttempts 次"

/** 重发倒计时文案：`重新发送（52s）`。 */
fun resendCountdownText(seconds: Int): String = "重新发送（${seconds}s）"

/** 倒计时归零后是否可重发。 */
fun canResendCode(secondsLeft: Int): Boolean = secondsLeft <= 0

/** 验证码错误页的呈现状态（错误次数与倒计时由宿主推进）。 */
data class CodeErrorUiState(
    val phoneMasked: String = "+86 138****0000",
    val code: String = "1 2 3 4 5 6",
    val remainingAttempts: Int = 4,
    val resendSeconds: Int = 52,
) {
    /** 去掉空格后的验证码位数。 */
    val codeDigits: String get() = code.filter { it.isDigit() }

    /** 6 位数字才允许提交（原型登录按钮为 disabled）。 */
    val canSubmit: Boolean get() = codeDigits.length == 6

    val errorText: String get() = codeErrorRemainingText(remainingAttempts)

    val resendText: String get() =
        if (canResendCode(resendSeconds)) "重新发送" else resendCountdownText(resendSeconds)
}

/** 异地登录信息。 */
data class MultiDeviceLogin(
    val time: String = "10月22日 09:12",
    val deviceName: String = "iPhone 15 Pro",
)

/** 多设备提示正文（时间与设备名由宿主注入）。 */
fun multiDeviceMessage(login: MultiDeviceLogin = MultiDeviceLogin()): String =
    "你的账号于 ${login.time} 在「${login.deviceName}」登录。非本人操作请立即改密码。"

// ---- 健康数据五问五答 ----

/** 健康数据说明的一条问答。 */
data class HealthInfoItem(val question: String, val answer: String)

/** 健康数据说明文案（v-healthinfo）。 */
object HealthInfoText {
    /** 五问五答，顺序与原型一致。 */
    val items: List<HealthInfoItem> = listOf(
        HealthInfoItem("收集哪些数据", "测量结果、健康档案、目标"),
        HealthInfoItem("如何使用", "趋势分析、身体报告、AI 解读"),
        HealthInfoItem("存储与加密", "本机加密 + 云端端到端加密（可选）"),
        HealthInfoItem("谁能看到", "仅你本人；家庭成员数据独立"),
        HealthInfoItem("保留多久", "账号存续期；删除/注销后按规则清除"),
    )
}

// ---- 共享原语 ----

/** 弹层遮罩：整屏半透明墨色，点击不关闭（关闭只走显式入口）。 */
@Composable
private fun LifecycleScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QkColor.Fg.copy(alpha = 0.45f))
            .padding(horizontal = QkSpacing.Xl),
        contentAlignment = Alignment.Center,
    ) {
        QkCard(padding = QkSpacing.Xl) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

/** 弹层图标 + 标题 + 正文（原型 .dic / .dt / .dd）。 */
@Composable
private fun LifecycleDialogBody(
    icon: ImageVector,
    tone: QkGlyphTone,
    title: String,
    body: String,
) {
    QkIconBubble(icon, tone, size = QkSpacing.Xl + QkSpacing.Lg)
    Text(
        title,
        modifier = Modifier.padding(top = QkSpacing.Lg),
        style = MaterialTheme.typography.titleLarge,
        color = QkColor.Fg,
        textAlign = TextAlign.Center,
    )
    Text(
        body,
        modifier = Modifier.padding(top = QkSpacing.Sm),
        style = MaterialTheme.typography.bodySmall,
        color = QkColor.Sub,
        textAlign = TextAlign.Center,
    )
}

/** 危险动作按钮（原型 .btn.danger）：主色换红的同尺寸按钮。 */
@Composable
private fun LifecycleDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(QkRadius.Button)
    Box(
        modifier = modifier
            .clip(shape)
            .background(QkColor.Danger)
            .clickable(onClick = onClick)
            .padding(horizontal = QkSpacing.CardPad, vertical = QkSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = QkColor.Card,
            maxLines = 1,
        )
    }
}

/** 居中状态页：图标 + 标题 + 正文 + 按钮组（原型 .statepage）。 */
@Composable
private fun LifecycleStatePage(
    icon: ImageVector,
    tone: QkGlyphTone,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    faded: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = if (faded) Modifier.fillMaxWidth().alpha(FadedAlpha) else Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkIconBubble(icon, tone, size = QkSpacing.Xl + QkSpacing.Lg)
            Text(
                title,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.padding(top = QkSpacing.Xl)) { actions() }
        }
    }
}

/** 状态页降级透明度（原型 .statepage 的 opacity .5 内层）。 */
private const val FadedAlpha = 0.5f

/** 提示横幅（原型 .banner.warn / .banner）：浅底圆角条 + 图标 + 小字。 */
@Composable
private fun LifecycleBanner(
    icon: ImageVector,
    text: String,
    tone: QkGlyphTone = QkGlyphTone.Green,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(if (tone == QkGlyphTone.Orange) QkColor.AccentSoft else QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(
            icon,
            tint = if (tone == QkGlyphTone.Orange) QkColor.AmberInk else QkColor.Primary,
            size = QkSpacing.Lg + QkSpacing.Xs,
        )
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.GhostInk,
        )
    }
}

// ---- 七屏 ----

/** v-forceupdate：强制更新弹层（只有「立即更新」）。 */
@Composable
fun ForceUpdateDialog(
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier,
    versions: AppVersionPair = AppVersionPair(),
) {
    LifecycleScrim {
        LifecycleDialogBody(
            icon = Icons.Outlined.CloudDownload,
            tone = QkGlyphTone.Green,
            title = AccountLifecycleText.ForceUpdateTitle,
            body = forceUpdateMessage(versions),
        )
        QkButton(
            text = AccountLifecycleText.ForceUpdateAction,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = onUpdateNow,
        )
    }
}

/** v-minver：系统版本过低整页。 */
@Composable
fun MinVersionScreen(
    onViewUpgradeMethod: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) { QkBackButton(onClick = onBack) }
        LifecycleStatePage(
            icon = Icons.Outlined.PhoneAndroid,
            tone = QkGlyphTone.Orange,
            title = AccountLifecycleText.MinVersionTitle,
            body = AccountLifecycleText.MinVersionBody,
            modifier = Modifier.padding(top = QkSpacing.Xl * 2),
            actions = {
                Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
                    QkButton(
                        text = AccountLifecycleText.MinVersionUpgrade,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onViewUpgradeMethod,
                    )
                    QkButton(
                        text = AccountLifecycleText.MinVersionBack,
                        modifier = Modifier.fillMaxWidth(),
                        style = QkButtonStyle.Ghost,
                        onClick = onBack,
                    )
                }
            },
        )
    }
}

/** v-privacy-lock：未同意隐私政策时的设备管理功能锁。 */
@Composable
fun PrivacyLockScreen(
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        AuthSubNavBar(title = AccountLifecycleText.PrivacyLockNavTitle, onBack = onBack)
        LifecycleBanner(
            icon = Icons.Outlined.Lock,
            text = AccountLifecycleText.PrivacyLockBanner,
            tone = QkGlyphTone.Orange,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        LifecycleStatePage(
            icon = Icons.Outlined.Lock,
            tone = QkGlyphTone.Gray,
            title = AccountLifecycleText.PrivacyLockTitle,
            body = AccountLifecycleText.PrivacyLockBody,
            modifier = Modifier.padding(top = QkSpacing.Xl),
            faded = true,
            actions = {
                QkButton(
                    text = AccountLifecycleText.PrivacyLockAction,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenPrivacy,
                )
            },
        )
    }
}

/** v-codeerr：验证码错误整页（剩余次数 + 重发倒计时 + 锁定提示）。 */
@Composable
fun CodeErrorScreen(
    state: CodeErrorUiState,
    onCodeChange: (String) -> Unit,
    onResendCode: () -> Unit,
    onLogin: () -> Unit,
    onNeedCodeHelp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        AuthSubNavBar(title = AccountLifecycleText.CodeNavTitle, onBack = onBack)
        Column(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            Text(
                AccountLifecycleText.CodeHeadline,
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
            )
            Text(
                AccountLifecycleText.CodeSentPrefix + state.phoneMasked,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
            )
        }
        QkTextField(
            value = state.code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            label = AccountLifecycleText.CodeLabel,
            placeholder = "请输入 6 位验证码",
        )
        Text(
            state.errorText,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Danger,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.resendText,
                modifier = Modifier.weight(1f).clickable(
                    enabled = canResendCode(state.resendSeconds),
                    onClick = onResendCode,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (canResendCode(state.resendSeconds)) QkColor.Primary else QkColor.Hint,
            )
            Text(
                AccountLifecycleText.CodeHelp,
                modifier = Modifier.clickable(onClick = onNeedCodeHelp),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Primary,
            )
        }
        LifecycleBanner(
            icon = Icons.Outlined.Warning,
            text = AccountLifecycleText.CodeLockBanner,
            tone = QkGlyphTone.Orange,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = AccountLifecycleText.CodeLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            onClick = onLogin,
        )
        Text(
            AccountLifecycleText.CodeBack,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = QkSpacing.Md),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.GhostInk,
            textAlign = TextAlign.Center,
        )
    }
}

/** v-expired：登录过期弹层。 */
@Composable
fun LoginExpiredDialog(
    onRelogin: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleScrim {
        LifecycleDialogBody(
            icon = Icons.Outlined.Lock,
            tone = QkGlyphTone.Orange,
            title = AccountLifecycleText.ExpiredTitle,
            body = AccountLifecycleText.ExpiredBody,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = AccountLifecycleText.ExpiredLater,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = onLater,
            )
            QkButton(
                text = AccountLifecycleText.ExpiredRelogin,
                modifier = Modifier.weight(1f),
                onClick = onRelogin,
            )
        }
    }
}

/** v-multidevice：账号在其他设备登录弹层。 */
@Composable
fun MultiDeviceDialog(
    onMine: () -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
    login: MultiDeviceLogin = MultiDeviceLogin(),
) {
    LifecycleScrim {
        LifecycleDialogBody(
            icon = Icons.Outlined.Warning,
            tone = QkGlyphTone.Orange,
            title = AccountLifecycleText.MultiDeviceTitle,
            body = multiDeviceMessage(login),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = AccountLifecycleText.MultiDeviceMine,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = onMine,
            )
            LifecycleDangerButton(
                text = AccountLifecycleText.MultiDeviceChangePassword,
                modifier = Modifier.weight(1f),
                onClick = onChangePassword,
            )
        }
    }
}

/** v-healthinfo：健康数据使用说明（五问五答 + 敏感信息提示）。 */
@Composable
fun HealthInfoScreen(
    onAcknowledge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        AuthSubNavBar(title = AccountLifecycleText.HealthInfoNavTitle, onBack = onBack)
        LifecycleBanner(
            icon = Icons.Outlined.Info,
            text = AccountLifecycleText.HealthInfoBanner,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.Sm) {
            HealthInfoText.items.forEachIndexed { index, item ->
                QkListRow(
                    title = item.question,
                    subtitle = item.answer,
                    showDivider = index != HealthInfoText.items.lastIndex,
                )
            }
        }
        LifecycleBanner(
            icon = Icons.Outlined.MonitorHeart,
            text = AccountLifecycleText.HealthInfoNote,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = AccountLifecycleText.HealthInfoAcknowledge,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAcknowledge,
        )
    }
}

/**
 * 七屏路由：按 [AccountBoundaryView] 呈现对应页面/弹层。
 *
 * 外部交接点（全部由既有流程实现）：
 * - [onUpdateNow] 跳应用商店/下载
 * - [onViewUpgradeMethod] 查看系统升级方法
 * - [onOpenPrivacy] 去同意隐私政策
 * - [onResendCode] 重新发送验证码
 * - [onNeedCodeHelp] 收不到验证码的帮助
 * - [onCodeLogin] 验证码登录
 * - [onRelogin] 重新登录
 * - [onLater] 登录过期弹层「稍后」
 * - [onMine] 异地登录「是我的」
 * - [onChangePassword] 异地登录「不是我，改密码」
 * - [onAcknowledgeHealthInfo] 健康数据说明「我知道了」
 * - [onDismiss] 可关闭页面的返回/关闭（强更不调用）
 */
@Composable
fun AccountLifecycleScreen(
    view: AccountBoundaryView,
    modifier: Modifier = Modifier,
    versions: AppVersionPair = AppVersionPair(),
    codeError: CodeErrorUiState = CodeErrorUiState(),
    multiDevice: MultiDeviceLogin = MultiDeviceLogin(),
    onUpdateNow: () -> Unit = {},
    onViewUpgradeMethod: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onCodeChange: (String) -> Unit = {},
    onResendCode: () -> Unit = {},
    onCodeLogin: () -> Unit = {},
    onNeedCodeHelp: () -> Unit = {},
    onRelogin: () -> Unit = {},
    onLater: () -> Unit = {},
    onMine: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    onAcknowledgeHealthInfo: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    when (view) {
        AccountBoundaryView.ForceUpdate -> Box(modifier = modifier) {
            ForceUpdateDialog(onUpdateNow = onUpdateNow, versions = versions)
        }

        AccountBoundaryView.MinVersion -> MinVersionScreen(
            onViewUpgradeMethod = onViewUpgradeMethod,
            onBack = onDismiss,
            modifier = modifier,
        )

        AccountBoundaryView.PrivacyLock -> PrivacyLockScreen(
            onOpenPrivacy = onOpenPrivacy,
            onBack = onDismiss,
            modifier = modifier,
        )

        AccountBoundaryView.CodeError -> CodeErrorScreen(
            state = codeError,
            onCodeChange = onCodeChange,
            onResendCode = onResendCode,
            onLogin = onCodeLogin,
            onNeedCodeHelp = onNeedCodeHelp,
            onBack = onDismiss,
            modifier = modifier,
        )

        AccountBoundaryView.LoginExpired -> Box(modifier = modifier) {
            LoginExpiredDialog(onRelogin = onRelogin, onLater = onLater)
        }

        AccountBoundaryView.MultiDevice -> Box(modifier = modifier) {
            MultiDeviceDialog(
                onMine = onMine,
                onChangePassword = onChangePassword,
                login = multiDevice,
            )
        }

        AccountBoundaryView.HealthInfo -> HealthInfoScreen(
            onAcknowledge = onAcknowledgeHealthInfo,
            onBack = onDismiss,
            modifier = modifier,
        )
    }
}
