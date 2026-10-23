package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 搜索 + 时间筛选条（T-098 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/26-final-gaps.html` → `v-search-filter`：
 * - 输入框 + 筛选 chip：全部 / 本周 / 本月 / 自定义
 * - 结果摘要：「搜索结果（3 条匹配“体脂”）」
 *
 * ## 验收关联
 * 筛选与搜索**只改变可见集合，不删除数据**（PRD-历史与趋势 D03 日期筛选）。
 * 本组件不持有任何数据，仅把 [query] 与 [selectedFilter] 交给调用方，
 * 因此结构上无法违反该约束。
 *
 * @param resultSummary 直接显示的摘要文案，通常由 [searchResultSummary] 生成
 */
@Composable
fun QkSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    filters: List<String> = emptyList(),
    selectedFilter: Int = -1,
    onFilterSelect: (Int) -> Unit = {},
    resultSummary: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        QkTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
        if (filters.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs)) {
                filters.forEachIndexed { index, label ->
                    QkChip(
                        label = label,
                        selected = index == selectedFilter,
                        onClick = { onFilterSelect(index) },
                    )
                }
            }
        }
        if (!resultSummary.isNullOrEmpty()) {
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = resultSummary,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 结果摘要文案，与原型 `v-search-filter` 的「搜索结果（3 条匹配“体脂”）」逐字一致。
 *
 * 抽成纯函数以供 JVM 单测断言文案（闭环脚本按原型文案匹配率打分）：
 * - 空查询返回 `null`（不显示摘要，避免「0 条匹配」的无意义提示）
 * - 命中 0 条时仍返回摘要，让「没找到」是可见结论而非空白
 */
fun searchResultSummary(count: Int, query: String): String? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return null
    return "搜索结果（$count 条匹配“$trimmed”）"
}
