package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import kotlinx.coroutines.delay

/**
 * 下拉刷新。对齐原型 26-final-gaps.html `v-pull-refresh`
 * （轻衡 · 最终补全交互原型里的列表顶部下拉）。
 *
 * 手势交给 Material3 [PullToRefreshBox]：下拉出现指示，松手越过阈值后回调 [onRefresh]。
 * [pullRefreshPhase] 把「列表顶部下拉 → 出现刷新图标 → 松手触发」收成可单测的四态。
 */

enum class PullRefreshPhase {
    Idle,
    Pulling,
    Armed,
    Refreshing,
}

/** 拖动比例 ≥ 1 视为已越过松手阈值。刷新中优先于拖动态。 */
fun pullRefreshPhase(dragFraction: Float, isRefreshing: Boolean): PullRefreshPhase = when {
    isRefreshing -> PullRefreshPhase.Refreshing
    dragFraction >= 1f -> PullRefreshPhase.Armed
    dragFraction > 0f -> PullRefreshPhase.Pulling
    else -> PullRefreshPhase.Idle
}

fun pullRefreshHint(phase: PullRefreshPhase): String = when (phase) {
    PullRefreshPhase.Idle -> "列表顶部下拉"
    PullRefreshPhase.Pulling -> "出现刷新图标"
    PullRefreshPhase.Armed -> "松手触发"
    PullRefreshPhase.Refreshing -> "下拉刷新中…"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QkPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = QkColor.Primary,
                containerColor = QkColor.Card,
            )
        },
        content = { content() },
    )
}

/** 原型 v-pull-refresh 的演示列表。业务页应直接使用 [QkPullRefresh]。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QkPullRefreshDemo(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var refreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(700)
            refreshing = false
        }
    }
    val phase = pullRefreshPhase(state.distanceFraction, refreshing)
    QkPullRefresh(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true },
        modifier = modifier.fillMaxSize(),
        state = state,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
        ) {
            QkPageTitle("下拉刷新演示")
            QkButton(
                text = "下拉刷新（点击模拟）",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { refreshing = true },
            )
            Text(
                pullRefreshHint(phase),
                modifier = Modifier.fillMaxWidth(),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            QkCard {
                PullRefreshRow("今天 07:30", "体脂 28.6%", "62.4 kg")
                PullRefreshRow("昨天 07:28", "体脂 28.8%", "62.7 kg")
                PullRefreshRow("10/19 07:22", "体脂 28.9%", "62.8 kg", showDivider = false)
            }
            QkCard {
                Text(
                    "下拉刷新在真实设备上：列表顶部下拉 → 出现刷新图标 → 松手触发。",
                    color = QkColor.PrimaryDark,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            QkButton(
                text = "返回",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun PullRefreshRow(time: String, detail: String, weight: String, showDivider: Boolean = true) {
    QkListRow(
        title = time,
        subtitle = detail,
        showDivider = showDivider,
        trailing = {
            Text(weight, color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
        },
    )
}

@Preview
@Composable
private fun QkPullRefreshPreview() {
    QkTheme { QkPullRefreshDemo() }
}
