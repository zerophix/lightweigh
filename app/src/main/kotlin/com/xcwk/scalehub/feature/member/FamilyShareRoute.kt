package com.xcwk.scalehub.feature.member

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme
import kotlin.random.Random

/**
 * member — 家庭邀请六屏的接线层（宿主页 = 我的 → 成员管理）。
 *
 * ## 为什么单列一份
 * [FamilyInviteScreen] 等六屏是呈现式组件：不碰剪贴板、不发网络、不落库。
 * 本文件是它们唯一的宿主：持有六屏之间的呈现状态（[FamilyShareState]）、
 * 把用户动作翻译成状态转移（[familyShareReduce]），并把复制 / 分享接上系统能力。
 *
 * ## 数据口径（不造后端）
 * 本仓库没有家庭共享的云端接口：邀请码在本机生成、接受邀请只走呈现流程，
 * 不改任何账号或成员数据；成员名与家庭名取原型默认值（[FamilyInviteText]）。
 * 这是当前数据层的真实能力，报告里已如实标注为「待后端接口」。
 */

/** 家庭共享的六个界面（顺序即原型 23 的六个 view）。 */
enum class FamilySharePage { Invite, InviteDone, Accept, AcceptConfirm, Joined, Revoke }

/** 邀请码位数（原型「6 位邀请码」）。 */
const val FAMILY_INVITE_CODE_LENGTH: Int = 6

/** 邀请码字符集：去掉易混淆的 0/O/1/I，便于口头转述。 */
private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** 生成本机邀请码（纯函数，随机源可注入以便 JVM 单测钉死长度与字符集）。 */
fun newFamilyInviteCode(random: Random = Random.Default): String =
    (1..FAMILY_INVITE_CODE_LENGTH)
        .map { INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)] }
        .joinToString("")

/**
 * 家庭共享六屏的呈现状态。`page == null` 表示未打开（停在成员管理页）。
 *
 * 三个子状态（邀请 / 接受 / 撤销）共用一份权限集合：邀请页勾选的共享范围
 * 与撤销页调整的范围是同一组语义（[FamilyPermission]），不让两页各存一份。
 */
data class FamilyShareState(
    val page: FamilySharePage? = null,
    val code: String = FamilyInviteText.SampleCode,
    val remainingMillis: Long = FAMILY_INVITE_CODE_TTL_MILLIS,
    val permissions: Set<FamilyPermission> = defaultFamilyPermissions(),
    val acceptInput: String = "",
    val showRevokeConfirm: Boolean = false,
) {
    /** 邀请页 / 邀请已发送页共用的渲染状态。 */
    val inviteState: FamilyInviteUiState
        get() = FamilyInviteUiState(code = code, remainingMillis = remainingMillis, permissions = permissions)

    /** 接受邀请页的输入状态。 */
    val acceptState: FamilyAcceptUiState get() = FamilyAcceptUiState(codeInput = acceptInput)

    /** 撤销共享页的渲染状态（当前权限即已生效值）。 */
    val revokeState: FamilyRevokeUiState
        get() = FamilyRevokeUiState(permissions = permissions, showRevokeConfirm = showRevokeConfirm)
}

/** 六屏之间的呈现态转移（外部副作用一律不进 reduce）。 */
sealed interface FamilyShareEvent {
    /** 打开某一屏（成员页的三个入口各对应一项）。 */
    data class Open(val page: FamilySharePage) : FamilyShareEvent

    /** 关闭整个流程，回到成员管理页。 */
    data object Close : FamilyShareEvent

    /** 邀请页「发送邀请」→ 邀请已发送。 */
    data object SendInvite : FamilyShareEvent

    /** 邀请已发送页「重新生成邀请码」→ 回邀请页并换一枚新码、重置 24 小时有效期。 */
    data class Regenerate(val code: String) : FamilyShareEvent

    /** 接受邀请页输入（只截取前 6 位）。 */
    data class CodeChanged(val text: String) : FamilyShareEvent

    /** 输入满 6 位才算「接受邀请」，进确认页；不足则原地不动。 */
    data object AcceptInvite : FamilyShareEvent

    /** 确认加入 → 已加入页。 */
    data object ConfirmJoin : FamilyShareEvent

    /** 权限勾选开关（邀请页与撤销页共用）。 */
    data class TogglePermission(val permission: FamilyPermission) : FamilyShareEvent

    /** 「撤销全部共享」先开二次确认弹层。 */
    data object RequestRevokeAll : FamilyShareEvent

    /** 二次确认后真正撤销：权限清空并退出流程。 */
    data object ConfirmRevokeAll : FamilyShareEvent

    /** 关闭二次确认弹层。 */
    data object CancelRevoke : FamilyShareEvent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - [FamilyShareEvent.AcceptInvite] 只接受 6 位邀请码（`FAMILY_INVITE_CODE_LENGTH`）
 * - [FamilyShareEvent.ConfirmRevokeAll] 清空全部共享权限并关闭流程（撤销后不再呈现旧权限）
 * - [FamilyShareEvent.RequestRevokeAll] 只开弹层，不提前改权限
 */
fun familyShareReduce(state: FamilyShareState, event: FamilyShareEvent): FamilyShareState = when (event) {
    is FamilyShareEvent.Open -> state.copy(page = event.page, showRevokeConfirm = false)
    FamilyShareEvent.Close -> state.copy(page = null, showRevokeConfirm = false)
    FamilyShareEvent.SendInvite ->
        if (state.page == FamilySharePage.Invite && state.inviteState.codeUsable) {
            state.copy(page = FamilySharePage.InviteDone)
        } else {
            state
        }

    is FamilyShareEvent.Regenerate -> state.copy(
        page = FamilySharePage.Invite,
        code = event.code,
        remainingMillis = FAMILY_INVITE_CODE_TTL_MILLIS,
    )

    is FamilyShareEvent.CodeChanged -> state.copy(acceptInput = event.text.take(FAMILY_INVITE_CODE_LENGTH))
    FamilyShareEvent.AcceptInvite ->
        if (state.acceptInput.length == FAMILY_INVITE_CODE_LENGTH) {
            state.copy(page = FamilySharePage.AcceptConfirm)
        } else {
            state
        }

    FamilyShareEvent.ConfirmJoin -> state.copy(page = FamilySharePage.Joined)
    is FamilyShareEvent.TogglePermission ->
        state.copy(permissions = toggleFamilyPermission(state.permissions, event.permission))

    FamilyShareEvent.RequestRevokeAll -> state.copy(showRevokeConfirm = true)
    FamilyShareEvent.ConfirmRevokeAll -> state.copy(
        page = null,
        permissions = emptySet(),
        showRevokeConfirm = false,
    )

    FamilyShareEvent.CancelRevoke -> state.copy(showRevokeConfirm = false)
}

/** 成员管理页上的三个入口文案（纯数据，JVM 单测校验）。 */
object FamilyShareEntry {
    /** 稳定标识（埋点 / 进程重建用）。 */
    const val ID_INVITE: String = "member.familyInvite"
    const val ID_ACCEPT: String = "member.familyAccept"
    const val ID_REVOKE: String = "member.familyRevoke"

    /** 邀请入口（→ [FamilyInviteScreen]）。 */
    const val INVITE_TITLE: String = "邀请家人加入"
    const val INVITE_HINT: String = "生成 24 小时有效的邀请码，设置共享范围"

    /** 接受邀请入口（→ [FamilyInviteAcceptScreen]）。 */
    const val ACCEPT_TITLE: String = "接受邀请"
    const val ACCEPT_HINT: String = "输入家人给的 6 位邀请码"

    /** 共享权限入口（→ [FamilyRevokeScreen]）。 */
    const val REVOKE_TITLE: String = "管理共享权限"
    const val REVOKE_HINT: String = "调整或撤销家人的查看权限"
}

/**
 * 六屏装配点：按 [initialPage] 起手，返回即退出流程。
 *
 * @param initialPage 成员页上点的是哪一个入口
 * @param onBack 回到成员管理页
 */
@Composable
fun FamilyShareRoute(
    initialPage: FamilySharePage,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(FamilyShareState(page = initialPage)) }
    val dispatch: (FamilyShareEvent) -> Unit = { state = familyShareReduce(state, it) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    when (state.page) {
        // 关闭流程即返回宿主页（状态已清空，在这里把控制权交回成员页）
        null -> LaunchedEffect(Unit) { onBack() }

        FamilySharePage.Invite -> FamilyInviteScreen(
            state = state.inviteState,
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Close) },
            onCopyCode = { clipboard.setText(AnnotatedString(state.code)) },
            onShareCode = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, inviteCodeRowText(state.code))
                }
                context.startActivity(Intent.createChooser(share, FamilyInviteText.NavInvite))
            },
            onTogglePermission = { dispatch(FamilyShareEvent.TogglePermission(it)) },
            onSendInvite = { dispatch(FamilyShareEvent.SendInvite) },
        )

        FamilySharePage.InviteDone -> FamilyInviteDoneScreen(
            state = state.inviteState,
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Close) },
            onReturnToFamilyShare = { dispatch(FamilyShareEvent.Close) },
            onRegenerateCode = { dispatch(FamilyShareEvent.Regenerate(newFamilyInviteCode())) },
        )

        FamilySharePage.Accept -> FamilyInviteAcceptScreen(
            state = state.acceptState,
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Close) },
            onCodeChanged = { dispatch(FamilyShareEvent.CodeChanged(it)) },
            onAcceptInvite = { dispatch(FamilyShareEvent.AcceptInvite) },
        )

        FamilySharePage.AcceptConfirm -> FamilyInviteConfirmScreen(
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Open(FamilySharePage.Accept)) },
            onConfirmJoin = { dispatch(FamilyShareEvent.ConfirmJoin) },
            onCancel = { dispatch(FamilyShareEvent.Open(FamilySharePage.Accept)) },
        )

        FamilySharePage.Joined -> FamilyInviteJoinedScreen(
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Close) },
            onEnterFamilyShare = { dispatch(FamilyShareEvent.Close) },
        )

        FamilySharePage.Revoke -> FamilyRevokeScreen(
            state = state.revokeState,
            modifier = modifier,
            onBack = { dispatch(FamilyShareEvent.Close) },
            onTogglePermission = { dispatch(FamilyShareEvent.TogglePermission(it)) },
            onSavePermissions = { dispatch(FamilyShareEvent.Close) },
            onRevokeAll = { dispatch(FamilyShareEvent.RequestRevokeAll) },
            onConfirmRevokeAll = { dispatch(FamilyShareEvent.ConfirmRevokeAll) },
            onCancelRevoke = { dispatch(FamilyShareEvent.CancelRevoke) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FamilyShareRoutePreview() {
    QkTheme { FamilyShareRoute(initialPage = FamilySharePage.Invite) }
}
