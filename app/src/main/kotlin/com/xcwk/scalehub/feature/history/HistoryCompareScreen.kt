package com.xcwk.scalehub.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/** 历史记录行（DEC-043-H 对比用）。 */
data class HistoryRow(
    val time: Long,
    val timeText: String,
    val weightText: String,
    val fatText: String,
)

/**
 * history-compare — 两条记录对比（DEC-043-H，参考 TrendPages.kt:570-578）。
 * 选两条记录，并排显示体重/体脂率/BMI 差异。
 */
@Composable
fun HistoryCompareScreen(
    onBack: () -> Unit = {},
    rows: List<HistoryRow> = emptyList(),
) {
    var selected by remember { mutableStateOf(setOf<Int>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(title = "记录对比", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        if (rows.isEmpty()) {
            QkCard { QkEmptyHint("暂无可对比的记录") }
            return
        }

        QkSectionHead(title = "选择两条记录（已选 ${selected.size}/2）", modifier = Modifier.padding(top = QkSpacing.Lg))
        rows.forEachIndexed { i, row ->
            val isSelected = i in selected
            QkCard(modifier = Modifier.padding(top = if (i == 0) 2.dp else 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.timeText, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
                        Text(
                            "体重 ${row.weightText} 公斤 · 体脂 ${row.fatText}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                    QkButton(
                        text = if (isSelected) "已选" else "选择",
                        style = if (isSelected) QkButtonStyle.Primary else QkButtonStyle.Secondary,
                        onClick = {
                            selected = if (isSelected) selected - i
                            else if (selected.size < 2) selected + i
                            else selected
                        },
                    )
                }
            }
        }

        // 对比结果
        if (selected.size == 2) {
            val (a, b) = selected.sorted().let { rows[it[0]] to rows[it[1]] }
            QkSectionHead(title = "对比结果", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                CompareRow("体重", a.weightText, b.weightText, "公斤")
                CompareRow("体脂率", a.fatText, b.fatText, "%")
                CompareRow("时间", a.timeText, b.timeText, "")
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun CompareRow(name: String, a: String, b: String, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(
            "$a $unit",
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
        Text("→", modifier = Modifier.padding(horizontal = 8.dp), color = QkColor.Muted)
        Text(
            "$b $unit",
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
    }
}