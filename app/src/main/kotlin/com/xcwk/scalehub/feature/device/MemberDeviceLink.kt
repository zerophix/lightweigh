package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * member-device — 成员设备关联（T-080 · 原型 24 `v-member-device`）。
 *
 * ## 原型布局
 * 导航条「成员设备关联」→ hero「成员与设备关联 / 管理哪些成员使用哪些设备」
 * →「设备」行（客厅体脂秤 / 已关联 3 位成员 / 管理 ›）→「关联成员」三人开关
 * → 说明横幅 →「保存关联」。
 *
 * ## 验收（AC-D5）
 * - 设备行展示已关联成员数与「管理 ›」入口
 * - 三位成员的勾选集合是纯状态（[toggleMemberLink]），保存经 `onLinkSaved(memberIds)` 上交
 * - 副标题「已关联 N 位成员」由勾选集合派生（[linkedMemberSummary]），不写死
 *
 * ## 边界（呈现式组件）
 * 关联关系不在本组件落库或调用设备/成员 API：本文件只维护勾选集合与呈现。
 */

const val MEMBER_DEVICE_TITLE = "成员设备关联"
const val MEMBER_DEVICE_HEADLINE = "成员与设备关联"
const val MEMBER_DEVICE_SUBLINE = "管理哪些成员使用哪些设备"
const val MEMBER_DEVICE_SECTION = "设备"
const val MEMBER_DEVICE_MANAGE = "管理 ›"
const val MEMBER_LINK_SECTION = "关联成员"
const val MEMBER_DEVICE_NAME = "客厅体脂秤"
const val MEMBER_DEFAULT_USAGE = "默认使用此设备"
const val MEMBER_DEVICE_NOTE = "一台体脂秤可关联多位成员，\n测量时会自动识别当前成员。"
const val MEMBER_LINK_SAVE = "保存关联"

/** 开关尺寸（原型 .sw 34x20 胶囊 / .knob 16 圆点）。 */
private val MemberLinkSwitchWidth = 34.dp
private val MemberLinkSwitchHeight = 20.dp
private val MemberLinkSwitchKnob = 16.dp
private val MemberLinkSwitchInset = 2.dp

/** 可关联成员：[defaultUser] 为 true 的成员副标题固定为「默认使用此设备」。 */
data class LinkableMember(
    val id: String,
    val name: String,
    val initial: String,
    val defaultUser: Boolean,
)

/** 成员行副标题：默认使用者用固定文案，其余成员共用当前设备名。 */
fun memberUsageText(member: LinkableMember, deviceName: String): String =
    if (member.defaultUser) MEMBER_DEFAULT_USAGE else "共用$deviceName"

/** 设备行副标题（由勾选集合大小派生，避免写死「已关联 3 位成员」）。 */
fun linkedMemberSummary(count: Int): String = "已关联 $count 位成员"

/** 原型 v-member-device 的三位成员（林悦为默认使用者）。 */
val DEFAULT_LINKABLE_MEMBERS = listOf(
    LinkableMember("linyue", "林悦", "悦", defaultUser = true),
    LinkableMember("mama", "妈妈", "妈", defaultUser = false),
    LinkableMember("baba", "爸爸", "爸", defaultUser = false),
)

/** 初始已关联集合（原型三人开关均为开）。 */
val DEFAULT_LINKED_MEMBER_IDS: Set<String> = setOf("linyue", "mama", "baba")

/** 成员设备关联页呈现状态：设备名 + 可关联成员 + 已勾选集合。 */
data class MemberDeviceLinkUiState(
    val deviceName: String = MEMBER_DEVICE_NAME,
    val members: List<LinkableMember> = DEFAULT_LINKABLE_MEMBERS,
    val linkedIds: Set<String> = DEFAULT_LINKED_MEMBER_IDS,
) {
    val linkedCount: Int get() = members.count { it.id in linkedIds }
    val linkedSummary: String get() = linkedMemberSummary(linkedCount)

    fun isLinked(memberId: String): Boolean = memberId in linkedIds
}

/**
 * 勾选集合切换纯函数 —— 未知成员 id 原样返回（防上游传入脏 id 造成幽灵勾选）。
 */
fun toggleMemberLink(state: MemberDeviceLinkUiState, memberId: String): MemberDeviceLinkUiState {
    if (state.members.none { it.id == memberId }) return state
    val next = if (memberId in state.linkedIds) state.linkedIds - memberId else state.linkedIds + memberId
    return state.copy(linkedIds = next)
}

/** 区块小标题（原型 .lbl）。 */
@Composable
private fun MemberSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = QkSpacing.Sm),
        style = MaterialTheme.typography.labelMedium,
        color = QkColor.Hint,
    )
}

/** 成员首字头像（原型 .av 40px 正圆；默认使用者薄荷底，其余浅灰底）。 */
@Composable
private fun MemberInitialAvatar(initial: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(DeviceAvatarSize)
            .clip(CircleShape)
            .background(if (highlighted) QkColor.Mint else QkColor.Secondary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) QkColor.Primary else QkColor.GhostInk,
        )
    }
}

/** 关联开关（原型 .sw：主绿胶囊 + 白色圆点；未关联为浅灰胶囊）。 */
@Composable
private fun MemberLinkSwitch(linked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = MemberLinkSwitchWidth, height = MemberLinkSwitchHeight)
            .clip(CircleShape)
            .background(if (linked) QkColor.Primary else QkColor.Secondary)
            .padding(horizontal = MemberLinkSwitchInset),
        contentAlignment = if (linked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(MemberLinkSwitchKnob)
                .clip(CircleShape)
                .background(QkColor.Card),
        )
    }
}

/** 成员设备关联页（原型 `v-member-device`）。 */
@Composable
fun MemberDeviceLinkScreen(
    state: MemberDeviceLinkUiState,
    onToggleMember: (String) -> Unit,
    onLinkSaved: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onManageDevice: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        DevicePageNavBar(MEMBER_DEVICE_TITLE, onBack)

        DeviceHero(
            headline = MEMBER_DEVICE_HEADLINE,
            subline = MEMBER_DEVICE_SUBLINE,
            modifier = Modifier.padding(bottom = QkSpacing.Lg),
        )

        // 设备行：图标 + 设备名 + 派生关联数 + 「管理 ›」入口
        MemberSectionLabel(MEMBER_DEVICE_SECTION)
        QkCard(padding = QkSpacing.Md) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkIconBubble(icon = QkIcons.Device, size = DeviceAvatarSize)
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(state.deviceName, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                    Text(
                        state.linkedSummary,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
                Text(
                    MEMBER_DEVICE_MANAGE,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onManageDevice)
                        .padding(horizontal = QkSpacing.Xs, vertical = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                )
            }
        }

        // 关联成员：三行开关，行整体可点（开关只做视觉指示）
        MemberSectionLabel(MEMBER_LINK_SECTION, modifier = Modifier.padding(top = QkSpacing.Md))
        QkCard(padding = QkSpacing.Md) {
            state.members.forEachIndexed { index, member ->
                if (index > 0) QkDivider()
                val linked = state.isLinked(member.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleMember(member.id) }
                        .padding(vertical = QkSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberInitialAvatar(member.initial, highlighted = member.defaultUser)
                    Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                        Text(member.name, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                        Text(
                            memberUsageText(member, state.deviceName),
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                    MemberLinkSwitch(linked = linked)
                }
            }
        }

        DeviceNoteBanner(
            text = MEMBER_DEVICE_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = MEMBER_LINK_SAVE,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onLinkSaved(state.linkedIds) },
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}
