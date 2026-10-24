package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/** 我的页「更多资料」入口行文案（T-054）。 */
const val MINE_EXTRAS_ENTRY_TITLE = "个人简介与展示标签"

/** 入口行副标题：说清点进去能看到什么。 */
const val MINE_EXTRAS_ENTRY_HINT = "补充个人简介、所在地，决定是否展示标签"

/** 更多资料页标题（组件内含「更多资料」分区头，页面标题不与其重复）。 */
const val MINE_EXTRAS_TITLE = "个人资料"

/**
 * mine-extras 宿主页（T-054）：我的页 →「个人简介与展示标签」→ 本页 → 返回。
 *
 * 三个字段的输入控件与文案都在既有 [ProfileExtraCard] 内，本页只做版式与返回路径。
 * 编辑值保存在本页草稿态：档案模型（[com.xcwk.scalehub.application.query.CurrentProfile]）
 * 目前只含性别/生日/身高/昵称，没有简介、所在地、展示标签三个字段，
 * 因此这里不假装已落库——写入通道需先扩档案模型，属后续任务。
 */
@Composable
fun MineExtrasScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var bio by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var showTag by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(modifier = Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(QkSpacing.Sm))
            Text(
                text = MINE_EXTRAS_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.height(QkSpacing.Lg))
        ProfileExtraCard(
            bio = bio,
            location = location,
            showTag = showTag,
            onBioChange = { bio = it },
            onLocationChange = { location = it },
            onShowTagChange = { showTag = it },
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

@Preview(name = "个人资料 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MineExtrasScreenPreviewDefault() {
    QkTheme {
        MineExtrasScreen()
    }
}
