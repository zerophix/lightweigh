package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * ai — [AiBoundariesScreen] 的接入装配点（T-085 接入波）。
 *
 * ## 为什么带一层「示例清单」
 * 边界屏本身按 `state.stage` 渲染六屏之一，进入时只可能看到其中一屏；
 * 没有真实 AI 服务可触发其余五态时，用一张清单把六态逐个放出来，
 * 用户与设计走查都能点到每一屏。清单只做导航，不改边界屏内部。
 *
 * ```
 * AI 问答页 ──「AI 边界与降级示例」──► 六态清单 ──┬─ 服务不可用
 *                                                ├─ 报告已过期
 *                                                ├─ 正在重新分析
 *                                                ├─ 新报告已生成
 *                                                ├─ 未开启个性化
 *                                                └─ 成员未授权
 * ```
 *
 * ## 边界
 * 状态仍由 [aiBoundaryStateOf] / [aiBoundaryReduce] 推导，这里不做业务判断；
 * 边界解除（`stage == null`）时回到清单，而不是留一屏空白。
 */

/** 清单里的一条示例：标题 + 副标题 + 初始状态构造器。 */
data class AiBoundaryDemo(
    val title: String,
    val hint: String,
    val build: () -> AiBoundaryUiState,
)

/** 六态示例（顺序对齐原型 25 的六个 view）。 */
val AI_BOUNDARY_DEMOS: List<AiBoundaryDemo> = listOf(
    AiBoundaryDemo(
        title = "AI 服务不可用",
        hint = "维护中，可重试连接或先看历史报告",
        build = { aiBoundaryStateOf(availability = AiServiceAvailability.Unavailable) },
    ),
    AiBoundaryDemo(
        title = "报告已过期",
        hint = "有新测量记录未纳入，可重新分析",
        build = { aiBoundaryStateOf(freshness = AiDataFreshness.Stale) },
    ),
    AiBoundaryDemo(
        title = "正在重新分析",
        hint = "分析中的等待态",
        build = {
            aiBoundaryStateOf(
                freshness = AiDataFreshness.Stale,
                reanalysis = AiReanalysisPhase.Running,
            )
        },
    ),
    AiBoundaryDemo(
        title = "新报告已生成",
        hint = "基于最新数据的结论与修正说明",
        build = { aiBoundaryStateOf(reanalysis = AiReanalysisPhase.Done) },
    ),
    AiBoundaryDemo(
        title = "未开启个性化分析",
        hint = "只答通用健康知识，不读健康数据",
        build = { aiBoundaryStateOf(scope = AiDataScope.GenericOnly) },
    ),
    AiBoundaryDemo(
        title = "成员未授权 AI 解读",
        hint = "当前查看的家庭成员未授权，可邀请",
        build = { aiBoundaryStateOf(scope = AiDataScope.MemberUnauthorized) },
    ),
)

/**
 * 边界示例接入点。
 *
 * @param onBack 退出示例（清单页返回键与各边界屏返回键共用）
 */
@Composable
fun AiBoundariesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // -1 = 停在清单；其余为示例下标（不存对象，进程重建后按相同顺序重建）
    var demoIndex by rememberSaveable { mutableStateOf(-1) }
    var state by remember { mutableStateOf(aiBoundaryStateOf()) }

    val demo = AI_BOUNDARY_DEMOS.getOrNull(demoIndex)
    if (demo == null || state.stage == null) {
        AiBoundaryDemoList(
            onPick = { index ->
                demoIndex = index
                state = AI_BOUNDARY_DEMOS[index].build()
            },
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        AiBoundariesScreen(
            state = state,
            onIntent = { state = aiBoundaryReduce(state, it) },
            modifier = modifier,
            onBack = onBack,
            // 本轮没有真实服务：重试直接回传「连接已恢复」，边界解除后回清单
            onRetryConnection = { state = aiBoundaryReduce(state, AiBoundaryIntent.ConnectionRestored) },
            onStartReanalysis = { state = aiBoundaryReduce(state, AiBoundaryIntent.StartReanalysis) },
            onOpenPersonalization = {
                state = aiBoundaryReduce(state, AiBoundaryIntent.PersonalizationEnabled)
            },
            onSwitchMember = {
                state = aiBoundaryReduce(state, AiBoundaryIntent.MemberAuthorizationGranted)
            },
            onInviteMember = {
                state = aiBoundaryReduce(state, AiBoundaryIntent.MemberAuthorizationGranted)
            },
        )
    }
}

/** 六态清单：一行一态（标题 + 能看到的边界形态）。 */
@Composable
private fun AiBoundaryDemoList(
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = AiBoundaryText.TitleAssistant,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        QkCard {
            AI_BOUNDARY_DEMOS.forEachIndexed { index, demo ->
                QkListRow(
                    title = demo.title,
                    subtitle = demo.hint,
                    showDivider = index != AI_BOUNDARY_DEMOS.lastIndex,
                    onClick = { onPick(index) },
                )
            }
        }
        Text(
            text = AiBoundaryText.Disclaimer,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}
