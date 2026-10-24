package com.xcwk.scalehub.feature.member

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * member — 家庭邀请六屏（T-088 · 原型23）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/23-family-invite.html`
 *   v-invite / v-invite-done / v-accept / v-accept-confirm / v-accept-done / v-revoke
 * - 验收：AC-J1-15「家庭邀请流程」——六个 view 文案逐字取自原型，禁止改写
 *
 * ## 呈现式约束（AC-J1-15 forbidden 原文，硬约束）
 * 1. 六个 Screen 纯渲染：生成/复制/分享邀请码、接受邀请、保存或撤销权限一律经传入回调，
 *    本文件不接触剪贴板、系统分享、网络或数据库 API（单测源码守卫兜底）；
 * 2. 邀请码 24 小时有效期与权限勾选为纯函数（[inviteCodeUsable] / [toggleFamilyPermission]），
 *    倒计时归零后邀请码置为不可用，页面仅剩「重新生成邀请码」出口；
 * 3. 撤销全部共享必须经二次确认弹层（[FamilyRevokeUiState.showRevokeConfirm]），确认回调才执行撤销；
 * 4. 颜色/间距一律 Qk token（gov-prototype-verify --strict 同口径，单测源码守卫兜底）。
 */

/** 共享权限三项（邀请页与撤销页同一组语义，两套文案见 [FamilyInviteText]）。 */
enum class FamilyPermission { Records, Report, Ai }

/** 原型 23 全部六屏文案（逐字取自 HTML，勿改）。 */
object FamilyInviteText {
    // ---- v-invite（生成邀请码）----
    const val NavInvite = "邀请家人"
    const val InviteTitle = "邀请家人加入"
    const val InviteSubtitle = "生成邀请码，分享给家人"
    const val CodeHint = "邀请码（24 小时内有效）"
    const val SampleCode = "QH8K2M"
    const val Copy = "复制"
    const val Share = "分享给家人"
    const val InvitePermissionSection = "共享权限设置"
    const val InvitePermissionRecords = "允许查看测量记录"
    const val InvitePermissionReport = "允许查看身体报告"
    const val InvitePermissionAi = "允许查看 AI 解读"
    const val CodeNoticeLine1 = "邀请码仅用于身份验证，"
    const val CodeNoticeLine2 = "不包含任何健康数据。"
    const val SendInvite = "发送邀请"

    // ---- v-invite-done（邀请已发送）----
    const val NavInviteDone = "邀请已发送"
    const val SentTitle = "邀请已发送"
    const val SentSubtitle = "等待家人接受邀请"
    const val PendingInvite = "待接受邀请"
    const val InviteCodePrefix = "邀请码"
    const val ExpirySuffix = "小时后过期"
    const val PendingTag = "待接受"
    const val NoticeFamilyShareBefore = "家人接受后，你可以在"
    const val FamilyShareEntry = "家庭共享"
    const val NoticeFamilyShareAfter = "中"
    const val NoticeManageLine = "管理他的查看权限。"
    const val ReturnToFamilyShare = "返回家庭共享"
    const val RegenerateCode = "重新生成邀请码"

    // ---- v-accept（接受邀请）----
    const val NavAccept = "接受邀请"
    const val AcceptTitle = "输入邀请码"
    const val AcceptSubtitle = "向家人索取邀请码后输入"
    const val CodeInputLabel = "邀请码"
    const val CodePlaceholder = "6 位邀请码"
    const val AcceptNoticeLine1 = "接受邀请后，对方可以查看你授权的数据。"
    const val AcceptNoticeLine2 = "你可以随时在设置中撤销。"
    const val AcceptAction = "接受邀请"

    // ---- v-accept-confirm（确认加入）----
    const val NavJoinConfirm = "确认加入"
    const val InviterName = "林悦"
    const val JoinInviteSuffix = "邀请你加入"
    const val FamilyName = "林悦的家庭"
    const val WillViewSection = "对方将可以查看"
    const val WillViewRecords = "你的测量记录"
    const val WillViewReport = "你的身体报告"
    const val WillViewAiUnauthorized = "AI 解读（未授权）"
    const val JoinNoticeLine1 = "接受后对方可以查看你的健康数据，"
    const val JoinNoticeLine2 = "你可以随时在设置中撤销共享。"
    const val ConfirmJoin = "确认加入"
    const val Cancel = "取消"

    // ---- v-accept-done（已加入）----
    const val NavJoined = "已加入"
    const val JoinedTitlePrefix = "已加入"
    const val JoinedSubtitleBefore = "现在可以查看"
    const val JoinedSubtitleAfter = "的健康数据"
    const val JoinedSection = "你现在可以"
    const val JoinedItemPrefix = "查看"
    const val JoinedNoticeLine1 = "你的数据仍然独立保存，"
    const val JoinedNoticeLine2 = "不会自动共享给对方。"
    const val EnterFamilyShare = "进入家庭共享"

    // ---- v-revoke（撤销共享）----
    const val NavRevoke = "撤销共享"
    const val RevokeMemberGlyph = "妈"
    const val RevokeMemberName = "妈妈"
    const val CurrentViewLine = "当前可查看：测量记录 + 身体报告"
    const val RevokePermissionSection = "调整查看权限"
    const val RevokePermissionRecords = "查看测量记录"
    const val RevokePermissionReport = "查看身体报告"
    const val RevokePermissionAi = "查看 AI 解读"
    const val RevokeNoticeLine1 = "关闭全部权限后，"
    const val RevokeNoticeLine2Suffix = "将无法再查看你的数据。"
    const val SavePermissions = "保存权限"
    const val RevokeAll = "撤销全部共享"
    const val RevokeWarnTitle = "撤销全部共享？"
    const val RevokeDialogLine1Suffix = "将立即无法查看你的任何健康数据。"
    const val RevokeDialogLine2 = "此操作可重新邀请恢复。"
    const val ConfirmRevokeAll = "确认撤销"
}

/** 邀请码有效期 24 小时（原型「24 小时内有效」/「24 小时后过期」）。 */
const val FAMILY_INVITE_CODE_TTL_MILLIS: Long = 24 * 60 * 60 * 1000L

/** 邀请码剩余有效期是否可用：倒计时归零即不可用，仅剩「重新生成邀请码」。 */
fun inviteCodeUsable(remainingMillis: Long): Boolean = remainingMillis > 0

/** 原型文案「24 小时后过期」：剩余小时数（不足 1 小时按 0，封顶 24）。 */
fun inviteExpiryText(remainingMillis: Long): String =
    "${(remainingMillis / 3_600_000L).coerceIn(0L, 24L)} ${FamilyInviteText.ExpirySuffix}"

/** 原型文案「邀请码 QH8K2M」。 */
fun inviteCodeRowText(code: String): String = "${FamilyInviteText.InviteCodePrefix} $code"

/** 原型文案「家人接受后，你可以在「家庭共享」中」（入口名由调用方传入）。 */
fun noticeEntryLine(entry: String): String =
    FamilyInviteText.NoticeFamilyShareBefore + "「" + entry + "」" + FamilyInviteText.NoticeFamilyShareAfter

/** 原型文案「林悦 邀请你加入」。 */
fun joinInviteTitle(inviter: String): String = "$inviter ${FamilyInviteText.JoinInviteSuffix}"

/** 原型文案「「林悦的家庭」」（引号为原型原文的一部分）。 */
fun familyQuoteLine(familyName: String): String = "「$familyName」"

/** 原型文案「已加入「林悦的家庭」」。 */
fun joinedTitleText(familyName: String): String = FamilyInviteText.JoinedTitlePrefix + familyQuoteLine(familyName)

/** 原型文案「现在可以查看林悦的健康数据」。 */
fun joinedSubtitleText(inviter: String): String =
    FamilyInviteText.JoinedSubtitleBefore + inviter + FamilyInviteText.JoinedSubtitleAfter

/** 已加入页「你现在可以」清单的三项语义（原型三项：测量记录 / 身体报告 / 趋势图）。 */
enum class FamilyJoinedItem(val label: String) {
    Records("测量记录"),
    Report("身体报告"),
    Trend("趋势图"),
}

/** 原型文案「查看林悦的测量记录」。 */
fun joinedItemText(inviter: String, item: FamilyJoinedItem): String =
    FamilyInviteText.JoinedItemPrefix + inviter + "的" + item.label

/** 原型文案「妈妈将无法再查看你的数据。」。 */
fun revokeWarnLine2(memberName: String): String = memberName + FamilyInviteText.RevokeNoticeLine2Suffix

/** 原型文案「妈妈将立即无法查看你的任何健康数据。」。 */
fun revokeDialogLine1(memberName: String): String = memberName + FamilyInviteText.RevokeDialogLine1Suffix

/** 原型默认勾选（v-invite 与 v-revoke 一致：测量记录与身体报告开、AI 解读关）。 */
fun defaultFamilyPermissions(): Set<FamilyPermission> =
    setOf(FamilyPermission.Records, FamilyPermission.Report)

/** 纯函数：切换一项权限勾选（邀请页与撤销页共用，状态变化只经回调上报）。 */
fun toggleFamilyPermission(
    current: Set<FamilyPermission>,
    permission: FamilyPermission,
): Set<FamilyPermission> = if (permission in current) current - permission else current + permission

/** 纯函数：撤销可用性判定——已无任何可查看权限时「撤销全部共享」无事可做，置为不可用。 */
fun revokeAllAvailable(current: Set<FamilyPermission>): Boolean = current.isNotEmpty()

/** v-invite / v-invite-done 输入状态（不可变，由上层传入）。 */
data class FamilyInviteUiState(
    val code: String = FamilyInviteText.SampleCode,
    val remainingMillis: Long = FAMILY_INVITE_CODE_TTL_MILLIS,
    val permissions: Set<FamilyPermission> = defaultFamilyPermissions(),
) {
    /** 倒计时归零 → 邀请码不可用（复制/分享/发送邀请随之禁用，只能重新生成）。 */
    val codeUsable: Boolean get() = inviteCodeUsable(remainingMillis)
}

/** v-accept 输入状态（邀请码由用户键入，接受动作经回调交给上层验证——邀请码不是身份凭证）。 */
data class FamilyAcceptUiState(val codeInput: String = "")

/** v-accept-confirm 输入状态（邀请人与家庭名由上层解析邀请码后传入）。 */
data class FamilyAcceptConfirmUiState(
    val inviterName: String = FamilyInviteText.InviterName,
    val familyName: String = FamilyInviteText.FamilyName,
)

/** v-accept-done 输入状态。 */
data class FamilyJoinedUiState(
    val inviterName: String = FamilyInviteText.InviterName,
    val familyName: String = FamilyInviteText.FamilyName,
)

/** v-revoke 输入状态（当前权限为已生效值，撤销确认弹层由 [showRevokeConfirm] 驱动）。 */
data class FamilyRevokeUiState(
    val memberGlyph: String = FamilyInviteText.RevokeMemberGlyph,
    val memberName: String = FamilyInviteText.RevokeMemberName,
    val currentViewLine: String = FamilyInviteText.CurrentViewLine,
    val permissions: Set<FamilyPermission> = defaultFamilyPermissions(),
    val showRevokeConfirm: Boolean = false,
)

/**
 * v-invite 生成邀请码页。
 *
 * @param onCopyCode 复制邀请码（剪贴板实现由调用方注入，本文件不接触剪贴板 API）
 * @param onShareCode 分享给家人（系统分享实现由调用方注入）
 * @param onTogglePermission 权限勾选变化（纯函数构建新集合，这里只上报）
 * @param onSendInvite 发送邀请
 */
@Composable
fun FamilyInviteScreen(
    state: FamilyInviteUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCopyCode: () -> Unit = {},
    onShareCode: () -> Unit = {},
    onTogglePermission: (FamilyPermission) -> Unit = {},
    onSendInvite: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavInvite, onBack = onBack)
        FamilyHero(
            icon = QkIcons.Account,
            title = FamilyInviteText.InviteTitle,
            subtitle = FamilyInviteText.InviteSubtitle,
        )
        QkCard(modifier = Modifier.padding(top = QkSpacing.Lg)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = FamilyInviteText.CodeHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Muted,
                )
                Text(
                    text = state.code,
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.codeUsable) QkColor.Primary else QkColor.Muted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
                ) {
                    QkButton(
                        text = FamilyInviteText.Copy,
                        modifier = Modifier.weight(1f),
                        style = QkButtonStyle.Secondary,
                        enabled = state.codeUsable,
                        onClick = onCopyCode,
                    )
                    QkButton(
                        text = FamilyInviteText.Share,
                        modifier = Modifier.weight(1f),
                        style = QkButtonStyle.Secondary,
                        enabled = state.codeUsable,
                        onClick = onShareCode,
                    )
                }
            }
        }
        Text(
            text = FamilyInviteText.InvitePermissionSection,
            modifier = Modifier.padding(top = QkSpacing.Lg, bottom = QkSpacing.Sm, start = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.Sub,
        )
        QkCard {
            FamilySwitchRow(
                label = FamilyInviteText.InvitePermissionRecords,
                checked = FamilyPermission.Records in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Records) },
            )
            FamilySwitchRow(
                label = FamilyInviteText.InvitePermissionReport,
                checked = FamilyPermission.Report in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Report) },
            )
            FamilySwitchRow(
                label = FamilyInviteText.InvitePermissionAi,
                checked = FamilyPermission.Ai in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Ai) },
            )
        }
        FamilyBanner(
            lines = listOf(FamilyInviteText.CodeNoticeLine1, FamilyInviteText.CodeNoticeLine2),
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.SendInvite,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.codeUsable,
            onClick = onSendInvite,
        )
    }
}

/**
 * v-invite-done 邀请已发送页。
 *
 * @param onReturnToFamilyShare 返回家庭共享
 * @param onRegenerateCode 重新生成邀请码（倒计时归零后这是唯一出口）
 */
@Composable
fun FamilyInviteDoneScreen(
    state: FamilyInviteUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onReturnToFamilyShare: () -> Unit = {},
    onRegenerateCode: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavInviteDone, onBack = onBack)
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(
                    icon = QkIcons.Habit,
                    tone = QkGlyphTone.Green,
                    contentDescription = FamilyInviteText.SentTitle,
                )
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(FamilyInviteText.SentTitle, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                    Text(
                        text = FamilyInviteText.SentSubtitle,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
            }
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            Column {
                Text(FamilyInviteText.PendingInvite, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QkIconBubble(
                        icon = QkIcons.About,
                        tone = QkGlyphTone.Orange,
                        contentDescription = FamilyInviteText.PendingTag,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                        Text(
                            text = inviteCodeRowText(state.code),
                            style = MaterialTheme.typography.titleSmall,
                            color = QkColor.Fg,
                        )
                        Text(
                            text = inviteExpiryText(state.remainingMillis),
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                    QkPill(text = FamilyInviteText.PendingTag, tone = QkPillTone.Amber)
                }
            }
        }
        FamilyBanner(
            lines = listOf(
                noticeEntryLine(FamilyInviteText.FamilyShareEntry),
                FamilyInviteText.NoticeManageLine,
            ),
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.ReturnToFamilyShare,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReturnToFamilyShare,
        )
        QkButton(
            text = FamilyInviteText.RegenerateCode,
            modifier = Modifier.padding(top = QkSpacing.Md).align(Alignment.CenterHorizontally),
            style = QkButtonStyle.Ghost,
            onClick = onRegenerateCode,
        )
    }
}

/**
 * v-accept 接受邀请页。
 *
 * @param onCodeChanged 邀请码键入（只截 6 位，验证交给上层——邀请码不是身份凭证）
 * @param onAcceptInvite 接受邀请
 */
@Composable
fun FamilyInviteAcceptScreen(
    state: FamilyAcceptUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCodeChanged: (String) -> Unit = {},
    onAcceptInvite: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavAccept, onBack = onBack)
        FamilyHero(
            icon = QkIcons.Share,
            title = FamilyInviteText.AcceptTitle,
            subtitle = FamilyInviteText.AcceptSubtitle,
        )
        QkTextField(
            value = state.codeInput,
            onValueChange = { onCodeChanged(it.take(6)) },
            label = FamilyInviteText.CodeInputLabel,
            placeholder = FamilyInviteText.CodePlaceholder,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
        )
        FamilyBanner(
            lines = listOf(FamilyInviteText.AcceptNoticeLine1, FamilyInviteText.AcceptNoticeLine2),
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.AcceptAction,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAcceptInvite,
        )
    }
}

/**
 * v-accept-confirm 确认加入页。
 *
 * @param onConfirmJoin 确认加入（真正落库由上层处理）
 * @param onCancel 取消（回到输入邀请码页）
 */
@Composable
fun FamilyInviteConfirmScreen(
    state: FamilyAcceptConfirmUiState = FamilyAcceptConfirmUiState(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onConfirmJoin: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavJoinConfirm, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkGlyph(
                text = state.inviterName.take(1),
                tone = QkGlyphTone.Green,
                size = QkSpacing.Xl * 3,
            )
            Text(
                text = joinInviteTitle(state.inviterName),
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                text = familyQuoteLine(state.familyName),
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Lg)) {
            Column {
                Text(
                    FamilyInviteText.WillViewSection,
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Column(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                    FamilyCheckRow(text = FamilyInviteText.WillViewRecords, granted = true)
                    FamilyCheckRow(text = FamilyInviteText.WillViewReport, granted = true)
                    FamilyCheckRow(text = FamilyInviteText.WillViewAiUnauthorized, granted = false)
                }
            }
        }
        FamilyBanner(
            lines = listOf(FamilyInviteText.JoinNoticeLine1, FamilyInviteText.JoinNoticeLine2),
            warn = true,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.ConfirmJoin,
            modifier = Modifier.fillMaxWidth(),
            onClick = onConfirmJoin,
        )
        QkButton(
            text = FamilyInviteText.Cancel,
            modifier = Modifier.padding(top = QkSpacing.Md),
            style = QkButtonStyle.Secondary,
            onClick = onCancel,
        )
    }
}

/** v-accept-done 已加入页。 */
@Composable
fun FamilyInviteJoinedScreen(
    state: FamilyJoinedUiState = FamilyJoinedUiState(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEnterFamilyShare: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavJoined, onBack = onBack)
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(
                    icon = QkIcons.Habit,
                    tone = QkGlyphTone.Green,
                    contentDescription = FamilyInviteText.NavJoined,
                )
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(
                        text = joinedTitleText(state.familyName),
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        text = joinedSubtitleText(state.inviterName),
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
            }
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            Column {
                Text(FamilyInviteText.JoinedSection, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Column(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                    FamilyJoinedItem.values().forEach { item ->
                        FamilyCheckRow(text = joinedItemText(state.inviterName, item), granted = true)
                    }
                }
            }
        }
        FamilyBanner(
            lines = listOf(FamilyInviteText.JoinedNoticeLine1, FamilyInviteText.JoinedNoticeLine2),
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.EnterFamilyShare,
            modifier = Modifier.fillMaxWidth(),
            onClick = onEnterFamilyShare,
        )
    }
}

/**
 * v-revoke 撤销共享页（含二次确认弹层）。
 *
 * @param onTogglePermission 权限勾选变化（只上报，落库由上层处理）
 * @param onSavePermissions 保存权限
 * @param onRevokeAll 请求撤销全部共享（弹出二次确认，上层置 showRevokeConfirm=true）
 * @param onConfirmRevokeAll 二次确认后真正执行撤销
 * @param onCancelRevoke 关闭二次确认弹层
 */
@Composable
fun FamilyRevokeScreen(
    state: FamilyRevokeUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onTogglePermission: (FamilyPermission) -> Unit = {},
    onSavePermissions: () -> Unit = {},
    onRevokeAll: () -> Unit = {},
    onConfirmRevokeAll: () -> Unit = {},
    onCancelRevoke: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        FamilyTopBar(title = FamilyInviteText.NavRevoke, onBack = onBack)
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkGlyph(text = state.memberGlyph, tone = QkGlyphTone.Green)
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(state.memberName, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                    Text(
                        text = state.currentViewLine,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
            }
        }
        Text(
            text = FamilyInviteText.RevokePermissionSection,
            modifier = Modifier.padding(top = QkSpacing.Lg, bottom = QkSpacing.Sm, start = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.Sub,
        )
        QkCard {
            FamilySwitchRow(
                label = FamilyInviteText.RevokePermissionRecords,
                checked = FamilyPermission.Records in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Records) },
            )
            FamilySwitchRow(
                label = FamilyInviteText.RevokePermissionReport,
                checked = FamilyPermission.Report in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Report) },
            )
            FamilySwitchRow(
                label = FamilyInviteText.RevokePermissionAi,
                checked = FamilyPermission.Ai in state.permissions,
                onToggle = { onTogglePermission(FamilyPermission.Ai) },
            )
        }
        FamilyBanner(
            lines = listOf(FamilyInviteText.RevokeNoticeLine1, revokeWarnLine2(state.memberName)),
            warn = true,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = FamilyInviteText.SavePermissions,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSavePermissions,
        )
        QkButton(
            text = FamilyInviteText.RevokeAll,
            modifier = Modifier.padding(top = QkSpacing.Md),
            style = QkButtonStyle.Secondary,
            enabled = revokeAllAvailable(state.permissions),
            onClick = onRevokeAll,
        )
    }

    // 二次确认缺失时绝不执行撤销：确认撤销只在本弹层的确认按钮上回调。
    if (state.showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = onCancelRevoke,
            title = { Text(FamilyInviteText.RevokeWarnTitle) },
            text = {
                Text(revokeDialogLine1(state.memberName) + "\n" + FamilyInviteText.RevokeDialogLine2)
            },
            confirmButton = {
                TextButton(onClick = onConfirmRevokeAll) {
                    Text(FamilyInviteText.ConfirmRevokeAll, color = QkColor.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRevoke) { Text(FamilyInviteText.Cancel) }
            },
        )
    }
}

/** 六屏共用的页头（返回圆钮 + 页面标题，原型 .navbar 口径）。 */
@Composable
private fun FamilyTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        QkPageTitle(title = title, modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
    }
}

/** 六屏共用的 hero（图标泡 + 标题 + 副标题，原型 .hero.center 口径）。 */
@Composable
private fun FamilyHero(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon = icon, tone = QkGlyphTone.Green, contentDescription = title)
        Text(
            text = title,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 说明条（原型 .banner：浅底圆角 + 图标 + 两行正文；warn 为警示色调）。 */
@Composable
private fun FamilyBanner(lines: List<String>, modifier: Modifier = Modifier, warn: Boolean = false) {
    val ink = if (warn) QkColor.AmberInk else QkColor.Sub
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(if (warn) QkColor.AccentSoft else QkColor.Mint)
            .padding(QkSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        QkIcon(QkIcons.About, tint = ink)
        Column {
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = ink)
            }
        }
    }
}

/** 权限开关行（原型 .row + .sw：标题 + 右侧开关）。 */
@Composable
private fun FamilySwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = QkColor.Card,
                checkedTrackColor = QkColor.Primary,
                checkedBorderColor = QkColor.Primary,
                uncheckedThumbColor = QkColor.Card,
                uncheckedTrackColor = QkColor.Secondary,
                uncheckedBorderColor = QkColor.Secondary,
            ),
        )
    }
}

/** 授权清单行（原型 .check：勾选图标 + 正文；未授权项为灰色叹号）。 */
@Composable
private fun FamilyCheckRow(text: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        QkIcon(
            icon = if (granted) QkIcons.Habit else QkIcons.About,
            tint = if (granted) QkColor.Primary else QkColor.Muted,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (granted) QkColor.Sub else QkColor.Muted,
        )
    }
}
