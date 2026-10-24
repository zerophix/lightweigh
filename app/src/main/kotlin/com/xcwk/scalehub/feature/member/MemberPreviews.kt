package com.xcwk.scalehub.feature.member

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 家庭成员模块交付屏的 Compose 预览集合。
 *
 * `MembersRoute` / `MemberProfileRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 成员列表 · 默认态。 */
@Preview(name = "成员列表 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MembersScreenPreviewDefault() {
    QkTheme {
        MembersScreen(state = MembersUiState(), avatarOf = { "悦" }, onIntent = {})
    }
}

/** 成员资料编辑 · 默认态。 */
@Preview(name = "成员资料编辑 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberEditScreenPreviewDefault() {
    QkTheme {
        MemberEditScreen(memberName = "林悦", state = MemberProfileState(), onIntent = {})
    }
}

/** 成员资料保存失败（错误态）。 */
@Preview(name = "成员资料 · 保存失败", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberSaveFailScreenPreviewDefault() {
    QkTheme {
        MemberSaveFailScreen(
            memberName = "林悦",
            state = MemberProfileState(),
            onIntent = {},
            onBackToEdit = {},
        )
    }
}

/** 成员无测量记录（空态）。 */
@Preview(name = "成员 · 无记录", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberNoRecordScreenPreviewDefault() {
    QkTheme {
        MemberNoRecordScreen(memberName = "林悦", onBack = {})
    }
}

/** 成员无绑定设备（空态）。 */
@Preview(name = "成员 · 无设备", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberNoDeviceScreenPreviewDefault() {
    QkTheme {
        MemberNoDeviceScreen(memberName = "林悦", onBack = {})
    }
}

/** 行动计划 · 默认态。 */
@Preview(name = "行动计划 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ActionPlansScreenPreviewDefault() {
    QkTheme {
        ActionPlansScreen(state = ActionPlansUiState(), onIntent = {})
    }
}

/** 行动计划路由 · 默认态。 */
@Preview(name = "行动计划路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ActionPlansRoutePreviewDefault() {
    QkTheme {
        ActionPlansRoute(onBack = {})
    }
}

/** 家庭邀请 · 默认态。 */
@Preview(name = "家庭邀请 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyInviteScreenPreviewDefault() {
    QkTheme {
        FamilyInviteScreen(state = FamilyInviteUiState())
    }
}

/** 家庭邀请 · 已发出态。 */
@Preview(name = "家庭邀请 · 已发出", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyInviteDoneScreenPreviewDefault() {
    QkTheme {
        FamilyInviteDoneScreen(state = FamilyInviteUiState())
    }
}

/** 受邀接受页 · 默认态。 */
@Preview(name = "受邀接受 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyInviteAcceptScreenPreviewDefault() {
    QkTheme {
        FamilyInviteAcceptScreen(state = FamilyAcceptUiState())
    }
}

/** 受邀确认页 · 默认态。 */
@Preview(name = "受邀确认 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyInviteConfirmScreenPreviewDefault() {
    QkTheme {
        FamilyInviteConfirmScreen()
    }
}

/** 已加入家庭 · 默认态。 */
@Preview(name = "已加入家庭 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyInviteJoinedScreenPreviewDefault() {
    QkTheme {
        FamilyInviteJoinedScreen()
    }
}

/** 撤销成员权限 · 默认态。 */
@Preview(name = "撤销成员权限 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FamilyRevokeScreenPreviewDefault() {
    QkTheme {
        FamilyRevokeScreen(state = FamilyRevokeUiState())
    }
}
