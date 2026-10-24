package com.xcwk.scalehub.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 引导流程（开屏、条款、引导、权限、档案）交付屏的 Compose 预览集合。
 */

/** 开屏页 · 默认态。 */
@Preview(name = "开屏 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SplashScreenPreviewDefault() {
    QkTheme {
        SplashScreen(onEnter = {})
    }
}

/** 条款与隐私授权页 · 默认态。 */
@Preview(name = "条款授权 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TermsScreenPreviewDefault() {
    QkTheme {
        TermsScreen(state = TermsUiState(), onIntent = {})
    }
}

/** 引导总入口 · 默认态。 */
@Preview(name = "引导入口 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideScreenPreviewDefault() {
    QkTheme {
        GuideScreen()
    }
}

/** 引导流程 · 默认态。 */
@Preview(name = "引导流程 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideFlowPreviewDefault() {
    QkTheme {
        GuideFlow(state = GuideUiState(), onIntent = {})
    }
}

/** 引导 · 欢迎页。 */
@Preview(name = "引导 · 欢迎", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideIntroScreenPreviewDefault() {
    QkTheme {
        GuideIntroScreen(state = GuideUiState(), onIntent = {})
    }
}

/** 引导 · 目标选择页。 */
@Preview(name = "引导 · 目标", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideGoalScreenPreviewDefault() {
    QkTheme {
        GuideGoalScreen(state = GuideUiState(), onIntent = {})
    }
}

/** 引导 · 测量准备页。 */
@Preview(name = "引导 · 准备", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuidePrepareScreenPreviewDefault() {
    QkTheme {
        GuidePrepareScreen(state = GuideUiState(), onIntent = {})
    }
}

/** 引导 · 已连接页。 */
@Preview(name = "引导 · 已连接", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideConnectedScreenPreviewDefault() {
    QkTheme {
        GuideConnectedScreen(onIntent = {})
    }
}

/** 引导 · 完成页。 */
@Preview(name = "引导 · 完成", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideDoneScreenPreviewDefault() {
    QkTheme {
        GuideDoneScreen(onIntent = {})
    }
}

/** 引导 · 跳过页。 */
@Preview(name = "引导 · 跳过", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideSkippedScreenPreviewDefault() {
    QkTheme {
        GuideSkippedScreen(onIntent = {})
    }
}

/** 权限流程 · 默认态。 */
@Preview(name = "权限流程 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionFlowPreviewDefault() {
    QkTheme {
        PermissionFlow(state = PermissionFlowUiState(), onIntent = {})
    }
}

/** 权限说明页。 */
@Preview(name = "权限说明", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionExplainScreenPreviewDefault() {
    QkTheme {
        PermissionExplainScreen(onContinue = {})
    }
}

/** 权限已授予页 · 无更新弹窗。 */
@Preview(name = "权限已授予 · 无弹窗", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionGrantedScreenPreviewDefault() {
    QkTheme {
        PermissionGrantedScreen(
            updateDialogVisible = false,
            onShowUpdateDialog = {},
            onDismissUpdateDialog = {},
        )
    }
}

/** 权限已授予页 · 更新弹窗（弹层态）。 */
@Preview(name = "权限已授予 · 更新弹窗", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionGrantedScreenPreviewDialog() {
    QkTheme {
        PermissionGrantedScreen(
            updateDialogVisible = true,
            onShowUpdateDialog = {},
            onDismissUpdateDialog = {},
        )
    }
}

/** 权限被拒页（错误态）。 */
@Preview(name = "权限被拒", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionDeniedScreenPreviewDefault() {
    QkTheme {
        PermissionDeniedScreen(onOpenSettings = {}, onSkipConnection = {})
    }
}

/** 档案设置流程 · 默认态。 */
@Preview(name = "档案设置 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileSetupFlowPreviewDefault() {
    QkTheme {
        ProfileSetupFlow(state = ProfileSetupUiState(), onIntent = {})
    }
}

/** 档案 · 年龄性别页。 */
@Preview(name = "档案 · 年龄性别", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileAgeGenderScreenPreviewDefault() {
    QkTheme {
        ProfileAgeGenderScreen(state = ProfileSetupUiState(), onIntent = {})
    }
}

/** 档案 · 身高页。 */
@Preview(name = "档案 · 身高", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileHeightScreenPreviewDefault() {
    QkTheme {
        ProfileHeightScreen(state = ProfileSetupUiState(), onIntent = {}, onSkipProfile = {})
    }
}

/** 档案 · 完成页。 */
@Preview(name = "档案 · 完成", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileDoneScreenPreviewDefault() {
    QkTheme {
        ProfileDoneScreen()
    }
}
