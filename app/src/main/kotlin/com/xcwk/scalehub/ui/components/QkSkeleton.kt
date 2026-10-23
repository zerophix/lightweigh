package com.xcwk.scalehub.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 骨架屏。对齐原型 09-components-demo.html `v-skeleton`。
 *
 * 占位块保持加载前的版面，避免内容回来时跳动。色块只用次级底色，透明度呼吸。
 */

enum class QkSkeletonShape { Line, Circle, Block }

const val QkSkeletonTitle = "骨架屏加载"
const val QkSkeletonHint = "内容加载时保持布局稳定，避免跳动"
const val QkSkeletonBack = "返回组件演示"

@Composable
fun QkSkeleton(
    modifier: Modifier = Modifier,
    shape: QkSkeletonShape = QkSkeletonShape.Line,
    widthFraction: Float = 1f,
    height: Dp? = null,
) {
    val transition = rememberInfiniteTransition(label = "qk-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qk-skeleton-alpha",
    )
    val resolved = height ?: when (shape) {
        QkSkeletonShape.Line -> QkSpacing.Sm + QkSpacing.Xs
        QkSkeletonShape.Circle -> QkSpacing.Xl + QkSpacing.Md + QkSpacing.Xs
        QkSkeletonShape.Block -> QkSpacing.Xl * 4 + QkSpacing.Md
    }
    val clip = if (shape == QkSkeletonShape.Circle) CircleShape else RoundedCornerShape(QkRadius.SegmentItem)
    Box(
        modifier
            .fillMaxWidth(widthFraction.coerceIn(0.05f, 1f))
            .height(resolved)
            .clip(clip)
            .background(QkColor.Secondary.copy(alpha = alpha)),
    )
}

@Composable
fun QkSkeletonLine(widthFraction: Float, modifier: Modifier = Modifier, height: Dp = QkSpacing.Sm + QkSpacing.Xs) {
    QkSkeleton(modifier, QkSkeletonShape.Line, widthFraction, height)
}

@Composable
private fun QkSkeletonAvatarRow(titleFraction: Float, detailFraction: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        QkSkeleton(shape = QkSkeletonShape.Circle, widthFraction = 0.12f)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkSkeletonLine(titleFraction)
            QkSkeletonLine(detailFraction, height = QkSpacing.Sm)
        }
    }
}

/** 原型 v-skeleton：指标卡 + 两行列表 + 图表块。 */
@Composable
fun QkSkeletonPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
    ) {
        QkPageTitle(QkSkeletonTitle)
        Text(QkSkeletonHint, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        QkCard {
            QkSkeletonLine(0.35f)
            QkSkeletonLine(0.55f, modifier = Modifier.padding(top = QkSpacing.Md), height = QkSpacing.Xl + QkSpacing.Sm)
            QkSkeletonLine(0.4f, modifier = Modifier.padding(top = QkSpacing.Md))
        }
        QkCard {
            QkSkeletonAvatarRow(0.5f, 0.7f)
            QkSkeletonAvatarRow(0.45f, 0.65f)
        }
        QkCard {
            QkSkeleton(shape = QkSkeletonShape.Block)
        }
        QkButton(
            text = QkSkeletonBack,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        )
    }
}

@Preview
@Composable
private fun QkSkeletonPreview() {
    QkTheme { QkSkeletonPage() }
}
