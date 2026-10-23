package com.xcwk.scalehub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 骨架屏占位（T-099 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/09-components-demo.html` → `v-skeleton`：
 * 导航标题「骨架屏加载」，提示「内容加载时保持布局稳定，避免跳动」。
 * 三种形态：line（宽度可配百分比的文本行）、circle（头像位）、block（卡片块）。
 *
 * ## 布局稳定的可判定部分
 * 「加载后无跳动」= 占位尺寸只由调用方参数决定、与动画进度无关：
 * - [QkSkeleton] 的宽高在组合期由 [QkSkeletonShape] 与参数钉死，shimmer 只是
 *   [background] 的 Brush，不参与测量；
 * - shimmer 渐变窗口宽度恒等于占位自身宽度（[shimmerGradientRange]，有 JVM 单测），
 *   动画期间只有亮带平移，没有缩放。
 *
 * ## 用法
 * ```
 * QkSkeleton(shape = QkSkeletonShape.Line, widthPercent = 35f)                       // 标题行 35%
 * QkSkeleton(shape = QkSkeletonShape.Circle)                                          // 头像位
 * QkSkeleton(shape = QkSkeletonShape.Block, height = QkComponentSize.SkeletonBlock)   // 卡片块
 * ```
 */
enum class QkSkeletonShape {
    Line,
    Circle,
    Block,
}

private const val MIN_WIDTH_PERCENT = 5f
private const val MAX_WIDTH_PERCENT = 100f
private const val SHIMMER_DURATION_MS = 1200

/**
 * line 形态宽度百分比（0..100）→ 布局宽度比例（0..1）。
 * 纯逻辑：越界值夹取而非异常，空/错参数不应让骨架撑破布局。
 */
fun skeletonWidthFraction(widthPercent: Float): Float =
    widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT) / 100f

/**
 * shimmer 渐变窗口（px）：亮带从占位左侧外平移到右侧外。
 * [width] 直接持有而非由 endX 反推 —— 恒等于占位宽度，无浮点漂移。
 */
data class ShimmerRange(val startX: Float, val width: Float) {
    val endX: Float get() = startX + width
    /** 窗口宽度恒定 —— 动画只平移不缩放（布局稳定的可判定部分）。 */
    val windowWidth: Float get() = width
}

/**
 * shimmer 进度（0..1）→ 渐变窗口坐标。
 * 纯逻辑：进度越界夹取；窗口起点从 `-widthPx` 扫到 `2 * widthPx`，窗口宽度恒为 [widthPx]。
 */
fun shimmerGradientRange(progress: Float, widthPx: Float): ShimmerRange {
    val p = progress.coerceIn(0f, 1f)
    return ShimmerRange(startX = (p * 3f - 1f) * widthPx, width = widthPx)
}

/** shimmer 亮带：Border → Card → Border 三段渐变，无硬编码色值。 */
@Composable
fun qkShimmerBrush(progress: Float, widthPx: Float): Brush {
    val range = shimmerGradientRange(progress, widthPx)
    return Brush.linearGradient(
        colors = listOf(QkColor.Border, QkColor.Card, QkColor.Border),
        start = Offset(range.startX, 0f),
        end = Offset(range.endX, 0f),
    )
}

/**
 * 骨架占位本体。[height] 在 Circle 形态下表示直径。
 * 尺寸在测量期与动画无关：shimmer 不改变测量结果。
 */
@Composable
fun QkSkeleton(
    shape: QkSkeletonShape,
    modifier: Modifier = Modifier,
    widthPercent: Float = MAX_WIDTH_PERCENT,
    height: Dp = when (shape) {
        QkSkeletonShape.Line -> QkComponentSize.SkeletonLineTitle
        QkSkeletonShape.Circle -> QkComponentSize.SkeletonAvatar
        QkSkeletonShape.Block -> QkComponentSize.SkeletonBlock
    },
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val sizedModifier = when (shape) {
        QkSkeletonShape.Line ->
            modifier.fillMaxWidth(skeletonWidthFraction(widthPercent)).height(height)
        QkSkeletonShape.Circle -> modifier.size(height)
        QkSkeletonShape.Block -> modifier.fillMaxWidth().height(height)
    }
    val corner = when (shape) {
        QkSkeletonShape.Line -> RoundedCornerShape(QkSpacing.Xs)
        QkSkeletonShape.Circle -> CircleShape
        QkSkeletonShape.Block -> RoundedCornerShape(QkRadius.Button)
    }
    val progress = qkShimmerProgress()
    Box(
        sizedModifier
            .onSizeChanged { widthPx = it.width }
            .background(
                brush = if (widthPx > 0) {
                    qkShimmerBrush(progress, widthPx.toFloat())
                } else {
                    Brush.horizontalGradient(listOf(QkColor.Border, QkColor.Border))
                },
                shape = corner,
            ),
    )
}

/** line 形态便捷入口：宽度按百分比配置（原型 35% / 55% / 40% 等）。 */
@Composable
fun QkSkeletonLine(
    widthPercent: Float = MAX_WIDTH_PERCENT,
    height: Dp = QkComponentSize.SkeletonLineTitle,
    modifier: Modifier = Modifier,
) {
    QkSkeleton(
        shape = QkSkeletonShape.Line,
        modifier = modifier,
        widthPercent = widthPercent,
        height = height,
    )
}

/** circle 形态便捷入口：头像位（原型 38dp）。 */
@Composable
fun QkSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = QkComponentSize.SkeletonAvatar,
) {
    QkSkeleton(shape = QkSkeletonShape.Circle, modifier = modifier, height = size)
}

/** block 形态便捷入口：卡片块（原型 110dp）。 */
@Composable
fun QkSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = QkComponentSize.SkeletonBlock,
) {
    QkSkeleton(shape = QkSkeletonShape.Block, modifier = modifier, height = height)
}

/** 无限循环的 shimmer 进度（0..1，Restart 模式）。 */
@Composable
private fun qkShimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "qk-skeleton-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "qk-skeleton-shimmer-progress",
    )
    return progress
}
