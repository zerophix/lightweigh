package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 搜索筛选。对齐原型 26-final-gaps.html `v-search-filter`
 * （轻衡 · 最终补全交互原型 · 弹层）。
 *
 * 芯片文案是原型的全部 / 本周 / 本月 / 自定义。关键字匹配标题或副文。
 * 本周按近 7 天（daysAgo 0..6）、本月按近 31 天（0..30）；
 * 自定义没有原型默认区间，未传入闭区间时不命中。
 * 演示列表只按关键字收窄，点芯片给出「已筛选」反馈，与原型点击后列表不消失一致。
 */

enum class QkSearchFilter(val label: String) {
    All("全部"),
    Week("本周"),
    Month("本月"),
    Custom("自定义"),
}

data class QkSearchRecord(
    val title: String,
    val detail: String,
    val daysAgo: Int,
)

const val QkSearchPlaceholder = "搜索指标、日期、备注…"

fun matchesSearchQuery(record: QkSearchRecord, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return record.title.contains(q) || record.detail.contains(q)
}

fun inSearchWindow(daysAgo: Int, filter: QkSearchFilter, custom: IntRange? = null): Boolean = when (filter) {
    QkSearchFilter.All -> true
    QkSearchFilter.Week -> daysAgo in 0..6
    QkSearchFilter.Month -> daysAgo in 0..30
    QkSearchFilter.Custom -> custom?.contains(daysAgo) ?: false
}

fun filterSearchRecords(
    records: List<QkSearchRecord>,
    query: String,
    filter: QkSearchFilter,
    custom: IntRange? = null,
    applyWindow: Boolean = true,
): List<QkSearchRecord> = records.filter { record ->
    matchesSearchQuery(record, query) &&
        (!applyWindow || inSearchWindow(record.daysAgo, filter, custom))
}

fun searchResultCaption(matchCount: Int, query: String): String {
    val q = query.trim()
    return if (q.isEmpty()) {
        "搜索结果（$matchCount 条）"
    } else {
        "搜索结果（$matchCount 条匹配\"$q\"）"
    }
}

fun searchFilterFeedback(filter: QkSearchFilter): String? = when (filter) {
    QkSearchFilter.All -> null
    else -> "已筛选：${filter.label}"
}

fun prototypeSearchRecords(): List<QkSearchRecord> = listOf(
    QkSearchRecord("体脂率 28.6%", "10/21 07:30 · 标准范围", daysAgo = 2),
    QkSearchRecord("体脂率 28.8%", "10/20 07:28 · 标准范围", daysAgo = 3),
    QkSearchRecord("体脂率 28.9%", "10/19 07:22 · 标准范围", daysAgo = 4),
)

@Composable
fun QkSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: QkSearchFilter,
    onSelect: (QkSearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = QkSearchPlaceholder,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
        QkTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            leading = { QkIcon(QkIcons.Search, tint = QkColor.Muted, size = QkSpacing.Lg) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs)) {
            QkSearchFilter.entries.forEach { filter ->
                QkChip(
                    label = filter.label,
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                )
            }
        }
    }
}

/** 原型 v-search-filter。 */
@Composable
fun QkSearchFilterDemo(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var query by remember { mutableStateOf("体脂") }
    var filter by remember { mutableStateOf(QkSearchFilter.All) }
    val hits = filterSearchRecords(prototypeSearchRecords(), query, filter, applyWindow = false)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
    ) {
        QkPageTitle("搜索筛选")
        QkSearchBar(query = query, onQueryChange = { query = it }, selected = filter, onSelect = { filter = it })
        searchFilterFeedback(filter)?.let {
            Text(it, color = QkColor.Primary, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            searchResultCaption(hits.size, query),
            color = QkColor.FieldLabel,
            style = MaterialTheme.typography.labelMedium,
        )
        QkCard {
            hits.forEachIndexed { index, hit ->
                QkListRow(
                    title = hit.title,
                    subtitle = hit.detail,
                    showDivider = index != hits.lastIndex,
                    trailing = { QkIcon(QkIcons.ChevronRight, size = QkSpacing.Lg) },
                )
            }
        }
        QkButton(
            text = "返回",
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        )
    }
}

@Preview
@Composable
private fun QkSearchFilterPreview() {
    QkTheme { QkSearchFilterDemo() }
}
