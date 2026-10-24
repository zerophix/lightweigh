package com.xcwk.scalehub.feature.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * AI 模块交付屏的 Compose 预览集合。
 *
 * 目的：把原型与实现的差异变成肉眼可查的事实，而不是靠记忆声称"基本一致"。
 * 所有预览只构造状态对象与空回调，不依赖 ViewModel、不发起任何网络请求。
 */

private val previewMemberIdentity = MemberIdentity(
    name = "林悦",
    glyph = "悦",
    relationLine = "本人 · 女 · 30岁 · 168cm",
    isCurrent = true,
)

/** AI 助手主屏 · 默认态。 */
@Preview(name = "AI 主屏 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiScreenPreviewDefault() {
    QkTheme {
        AiScreen(state = AiUiState(), onIntent = {})
    }
}

/** AI 能力边界页 · 默认态（模型不可用时的能力说明）。 */
@Preview(name = "AI 能力边界 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiBoundariesScreenPreviewDefault() {
    QkTheme {
        AiBoundariesScreen(state = AiBoundaryUiState(), onIntent = {})
    }
}

/** AI 能力边界路由（含连接失败重试入口）。 */
@Preview(name = "AI 能力边界路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiBoundariesRoutePreviewDefault() {
    QkTheme {
        AiBoundariesRoute(onBack = {})
    }
}

/** 周报总结页 · 默认态。 */
@Preview(name = "AI 周报 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiWeeklySummaryScreenPreviewDefault() {
    QkTheme {
        AiWeeklySummaryScreen(state = AiWeeklySummaryUiState())
    }
}

/** 成员权限详情页 · 本人（当前成员）态。 */
@Preview(name = "成员权限详情 · 本人", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberPermissionDetailScreenPreviewDefault() {
    QkTheme {
        MemberPermissionDetailScreen(
            state = MemberPermissionDetailUiState(identity = previewMemberIdentity),
        )
    }
}

/** 成员权限详情页 · 非当前成员（可移除成员）态。 */
@Preview(name = "成员权限详情 · 他人", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberPermissionDetailScreenPreviewOtherMember() {
    QkTheme {
        MemberPermissionDetailScreen(
            state = MemberPermissionDetailUiState(
                identity = previewMemberIdentity.copy(
                    name = "林小满",
                    glyph = "满",
                    relationLine = "家庭成员 · 女 · 12岁 · 152cm",
                    isCurrent = false,
                ),
            ),
        )
    }
}
