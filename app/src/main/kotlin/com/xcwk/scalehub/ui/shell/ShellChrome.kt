package com.xcwk.scalehub.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.xcwk.scalehub.ui.QK_BRAND
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 壳层外观（对齐原型 .topbar / .nav）。
 *
 * 只负责「长什么样」，不持有导航状态：页签与栈仍由 [NavState] 决定，
 * 本文件的两个组件都是无状态入参出参，便于单独预览。
 */

/** 顶栏：品牌字标 + 随页副标题 + 右侧通知铃。 */
@Composable
fun QkTopBar(subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 最小高度：品牌字标 + 副标题在 200% 字体缩放下可继续撑高
            .heightIn(min = 58.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 品牌字标：与原型一致，单词「轻衡」（旧名「轻衡健康」已废弃）
            Text(
                QK_BRAND,
                color = QkColor.Primary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 底栏：四页签 + 中央凸起加号（原型 .nav / .nav-add）。 */
@Composable
fun QkBottomNav(
    tab: MainTab,
    canPop: Boolean,
    onTab: (MainTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(QkColor.Card.copy(alpha = 0.94f))
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(QkColor.Border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 最小高度：页签图标 + 文字在 200% 字体缩放下可继续撑高
                .heightIn(min = 76.dp)
                .padding(horizontal = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(MainTab.HOME, tab, canPop, onTab)
            NavItem(MainTab.TREND, tab, canPop, onTab)
            CenterAddButton(onAdd)
            NavItem(MainTab.DEVICE, tab, canPop, onTab)
            NavItem(MainTab.MINE, tab, canPop, onTab)
        }
    }
}

@Composable
private fun NavItem(
    item: MainTab,
    current: MainTab,
    canPop: Boolean,
    onTab: (MainTab) -> Unit,
) {
    // 有覆盖页压栈时不高亮页签：此刻用户看的是覆盖层，不是页签内容
    val active = current == item && !canPop
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onTab(item) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = iconOf(item),
            contentDescription = item.label,
            tint = if (active) QkColor.Primary else QkColor.SegmentInactive,
            modifier = Modifier.size(21.dp),
        )
        Text(
            item.label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) QkColor.Primary else QkColor.SegmentInactive,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CenterAddButton(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(y = (-15).dp)
            .size(43.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(QkRadius.NavFab),
                ambientColor = QkColor.Primary.copy(alpha = 0.28f),
                spotColor = QkColor.Primary.copy(alpha = 0.28f),
            )
            .clip(RoundedCornerShape(QkRadius.NavFab))
            .background(QkColor.Primary)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = QkIcons.Add,
            contentDescription = "快捷记录",
            tint = Color.White,
            modifier = Modifier.size(25.dp),
        )
    }
}

private fun iconOf(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> QkIcons.Home
    MainTab.TREND -> QkIcons.Trend
    MainTab.DEVICE -> QkIcons.Device
    MainTab.MINE -> QkIcons.Mine
}
