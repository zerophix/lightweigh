package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * sync — 数据同步页（原型 08 `v-sync`，接入波第三批的同步区宿主页）。
 *
 * ## 位置说明（与任务口径的对应）
 * 原型把「数据冲突」入口挂在「数据同步」页（v-sync）下：v-network / v-service
 * 是这一页的两种降级态，冲突处理从本页进入。本文件就是这一页的接入形态：
 * 数据同步 → [SyncEntries.DataConflict] → [DataConflictRoute] → 返回本页。
 *
 * 已交付的 [NetworkStatusScreen] / [ServiceStatusScreen] 是整屏状态页，
 * 需要外部探测回调才成立，本轮不伪造网络状态、不在本页硬塞入口。
 *
 * ## 边界
 * 入口清单是纯数据（[SyncEntry] / [SyncEntries]），可在 JVM 单测里穷举；
 * 本页不读同步队列、不发网络请求。
 */

/** 导航条标题（原型 v-sync navbar 逐字）。 */
const val SYNC_CENTER_TITLE = "数据同步"

/** 首页接入波入口行文案。 */
const val SYNC_CENTER_ENTRY_TITLE = "数据同步"

/** 数据冲突入口文案与说明（宿主页上那一行，改文案只改这里）。 */
const val SYNC_CONFLICT_ENTRY_TITLE = "数据冲突处理"
const val SYNC_CONFLICT_ENTRY_HINT = "离线切换、多设备记录、同步冲突的六种处理方式"

/** 一条同步区入口。 */
data class SyncEntry(
    val id: String,
    val title: String,
    val hint: String,
)

/** 同步区入口清单（顺序即页面内的呈现顺序）。 */
object SyncEntries {
    val DataConflict = SyncEntry(
        id = "sync.dataConflict",
        title = SYNC_CONFLICT_ENTRY_TITLE,
        hint = SYNC_CONFLICT_ENTRY_HINT,
    )

    val All: List<SyncEntry> = listOf(DataConflict)
}

/** 数据同步页：入口 → 数据冲突六态，返回即回本页。 */
@Composable
fun SyncCenterRoute(onBack: () -> Unit = {}) {
    var showConflict by rememberSaveable { mutableStateOf(false) }
    if (showConflict) {
        DataConflictRoute(onBack = { showConflict = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = SYNC_CENTER_TITLE,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }

        QkSectionHead(title = "同步与冲突", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            val entries = SyncEntries.All
            entries.forEachIndexed { index, entry ->
                QkListRow(
                    title = entry.title,
                    subtitle = entry.hint,
                    showDivider = index != entries.lastIndex,
                    onClick = { showConflict = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}
