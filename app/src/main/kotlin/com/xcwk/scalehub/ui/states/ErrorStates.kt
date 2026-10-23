package com.xcwk.scalehub.ui.states

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkExpandableText
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkLongPressAction
import com.xcwk.scalehub.ui.components.QkLongPressSelectable
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 通用错误与边界状态。
 *
 * 对齐原型 15-states-errors.html（轻衡 · 剩余状态与组件交互原型，含弹层）。
 * [ErrorStatePage] 覆盖 prototype_ref 的 15 个 view。枢纽里的删除失败、设备占用、
 * 清空对话、分享报告是弹层，单独提供，不另造一屏。
 * 文案照原型句子，不把不存在的 AC-E2E 编号写成产品行为。
 */

enum class ErrorStateKind(val viewId: String, val navTitle: String) {
    RecEdit("v-rec-edit", "编辑记录"),
    Export("v-export", "导出结果"),
    Score("v-score", "身体评分"),
    Anomaly("v-anomaly", "体重趋势"),
    MultiMetric("v-multimetric", "趋势对比"),
    PairTimeout("v-pair-timeout", "连接设备"),
    ReportStale("v-report-stale", "AI 报告解读"),
    SyncFail("v-sync-fail", "数据同步"),
    FeedbackFail("v-fb-fail", "意见反馈"),
    ConnectGuide("v-connect-guide", "连接指南"),
    Troubleshoot("v-troubleshoot", "故障排查"),
    PrivacyQa("v-privacy-qa", "隐私与数据安全"),
    NotifLanding("v-notif-landing", "开始测量"),
    FullscreenLoad("v-fullscreen-load", "正在加载你的健康数据…"),
    LongPress("v-longpress", "长按 / 复制 / 展开"),
}

enum class ErrorDialogKind { DeleteFail, DeviceBusy, ClearChat, ShareReport }

enum class CenterTone { None, Warn, Danger, Spinner }

enum class TrendChartMode { AnomalyGap, WeightAndFat }

data class AboveNav(val title: String, val body: String, val time: String)

data class StateRow(
    val title: String,
    val detail: String? = null,
    val trailing: String? = null,
    val detailDanger: Boolean = false,
    val tone: QkGlyphTone = QkGlyphTone.Green,
)

sealed interface StateBlock {
    data class Banner(val text: String, val warn: Boolean = false, val link: String? = null) : StateBlock
    data class Note(val text: String) : StateBlock
    data class Locked(val label: String, val value: String) : StateBlock
    data class Fields(val timeLabel: String, val timeValue: String, val noteLabel: String, val note: String) : StateBlock
    data class FileCard(val name: String, val meta: String, val tag: String) : StateBlock
    data class FailCard(val title: String, val detail: String, val retry: String) : StateBlock
    data class Section(val title: String, val more: String? = null) : StateBlock
    data class Rows(val items: List<StateRow>, val numbered: Boolean = false) : StateBlock
    data class Score(
        val score: String,
        val caption: String,
        val bands: List<String>,
        val how: String,
        val weights: List<Pair<String, String>>,
    ) : StateBlock
    data class Chart(val mode: TrendChartMode, val legends: List<String> = emptyList()) : StateBlock
    data class Hero(val title: String, val subtitle: String) : StateBlock
    data class BigValue(val label: String, val value: String, val caption: String) : StateBlock
    data class Bullets(val title: String, val lines: List<String>) : StateBlock
    data class Headline(val text: String) : StateBlock
    data class TagLine(val tag: String, val caption: String) : StateBlock
    data class LongPress(
        val menuLabel: String,
        val body: String,
        val highlight: String,
        val actions: List<String>,
        val expandLabel: String,
        val collapsed: String,
        val expanded: String,
    ) : StateBlock
}

data class ErrorStateModel(
    val kind: ErrorStateKind,
    val showNav: Boolean = true,
    val aboveNav: AboveNav? = null,
    val centered: Boolean = false,
    val tone: CenterTone = CenterTone.None,
    val heading: String? = null,
    val body: String? = null,
    val footnote: String? = null,
    val blocks: List<StateBlock> = emptyList(),
    val primary: String? = null,
    val primaryStyle: QkButtonStyle = QkButtonStyle.Primary,
    val secondary: String? = null,
)

fun errorStateModel(kind: ErrorStateKind): ErrorStateModel = when (kind) {
    ErrorStateKind.RecEdit -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Locked("测量数值不可修改", "62.4 kg"),
            StateBlock.Fields("测量时间", "2026-10-21 07:30", "备注（可选）", "晨起、空腹、排便后测量"),
            StateBlock.Banner("仅时间与备注可编辑，数值来自设备。"),
        ),
        primary = "保存修改",
    )
    ErrorStateKind.Export -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.FileCard("健康数据_20261022.csv", "128 条 · 42 KB", "已生成"),
            StateBlock.Banner("下载链接发至邮箱，24 小时内有效。"),
            StateBlock.FailCard("上次导出失败", "邮箱发送超时", "重试"),
        ),
        primary = "下载文件",
    )
    ErrorStateKind.Score -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Score(
                score = "86",
                caption = "本次评分 · 良好区间",
                bands = listOf("需改善", "良好 80–90", "优秀"),
                how = "评分如何得出",
                weights = listOf(
                    "体脂率参考区间" to "30%",
                    "BMI" to "25%",
                    "肌肉量与水分率" to "25%",
                    "内脏脂肪等级" to "20%",
                ),
            ),
            StateBlock.Note("评分为估算参考，非医疗诊断。"),
        ),
    )
    ErrorStateKind.Anomaly -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Section("体重", "近30天 ›"),
            StateBlock.Chart(TrendChartMode.AnomalyGap),
            StateBlock.Rows(
                listOf(
                    StateRow("10/01 数据异常已剔除", "读数 68.9kg 超范围，未计入趋势", detailDanger = true, tone = QkGlyphTone.Orange),
                    StateRow("10/05–10/08 缺少数据", "该时段无记录，曲线为估算衔接", tone = QkGlyphTone.Orange),
                ),
            ),
            StateBlock.Note("异常值与缺测以虚线/空心点标注。"),
        ),
    )
    ErrorStateKind.MultiMetric -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Chart(TrendChartMode.WeightAndFat, listOf("体重 kg", "体脂率 %")),
            StateBlock.Banner("30 天内体重与体脂率同步下降，方向一致。"),
        ),
    )
    ErrorStateKind.PairTimeout -> ErrorStateModel(
        kind = kind,
        centered = true,
        tone = CenterTone.Warn,
        heading = "配对超时",
        body = "30 秒内未能建立连接。请轻踩秤面唤醒后重试。",
        primary = "重新配对",
        secondary = "返回",
    )
    ErrorStateKind.ReportStale -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Banner("你有 2 条新测量记录，本报告基于旧数据。", warn = true, link = "重新分析 ›"),
            StateBlock.TagLine("旧报告", "基于 10月20日数据"),
            StateBlock.Headline("近 30 天体重下降 1.4 kg。"),
            StateBlock.Bullets("重新分析后将更新", listOf("纳入最新测量", "更新趋势结论", "重新生成建议")),
            StateBlock.Note("旧报告保留在历史中，不会被覆盖。"),
        ),
        primary = "用最新数据重新分析",
    )
    ErrorStateKind.SyncFail -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Banner("1 条记录同步失败，其余 127 条已同步。", warn = true),
            StateBlock.Rows(
                listOf(
                    StateRow("10/21 07:30 · 62.4 kg", "上传失败 · 网络中断", trailing = "重试", detailDanger = true),
                    StateRow("10/20 07:28 · 62.7 kg", "已同步"),
                ),
            ),
            StateBlock.Banner("失败记录已存本机，联网自动重试。"),
        ),
        primary = "立即重试全部",
    )
    ErrorStateKind.FeedbackFail -> ErrorStateModel(
        kind = kind,
        centered = true,
        tone = CenterTone.Danger,
        heading = "反馈提交失败",
        body = "网络开小差了，反馈未发送。填写内容已保留。",
        primary = "重新提交",
        secondary = "复制反馈内容",
    )
    ErrorStateKind.ConnectGuide -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Rows(
                items = listOf(
                    StateRow("确认手机蓝牙已开启"),
                    StateRow("轻踩体脂秤唤醒", "屏幕点亮即进入可连接状态"),
                    StateRow("手机靠近秤（1 米内）"),
                    StateRow("App 内点击「搜索设备」"),
                ),
                numbered = true,
            ),
            StateBlock.Banner("首次配对后，轻踩唤醒即自动重连。"),
        ),
        primary = "我试试",
    )
    ErrorStateKind.Troubleshoot -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Hero("哪里出了问题？", "选择情况，逐步排查"),
            StateBlock.Rows(
                listOf(
                    StateRow("搜不到体脂秤", tone = QkGlyphTone.Orange),
                    StateRow("测量数据不准", tone = QkGlyphTone.Orange),
                    StateRow("屏幕不亮 / 无法开机", tone = QkGlyphTone.Orange),
                    StateRow("数据没有同步", tone = QkGlyphTone.Orange),
                ),
            ),
        ),
        primary = "以上都不是，联系客服",
        primaryStyle = QkButtonStyle.Secondary,
    )
    ErrorStateKind.PrivacyQa -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.Banner("数据加密存储，绝不出售或用于广告。"),
            StateBlock.Rows(
                listOf(
                    StateRow("数据存在哪里？", "本机加密 + 云端端到端加密"),
                    StateRow("谁能看到我的数据？", "仅你本人，客服也无法查看明文"),
                    StateRow("如何导出或删除？", "隐私与数据管理中随时操作"),
                    StateRow("家人会看到我的数据吗？", "家庭成员数据相互独立"),
                ),
            ),
        ),
        primary = "查看完整隐私政策",
        primaryStyle = QkButtonStyle.Secondary,
    )
    ErrorStateKind.NotifLanding -> ErrorStateModel(
        kind = kind,
        aboveNav = AboveNav("该测量啦 · 轻衡", "固定时间测量让趋势更准。", "现在"),
        blocks = listOf(
            StateBlock.Hero("早上好，林悦", "今天还没测量，赤脚上秤记录一次吧"),
            StateBlock.BigValue("上次测量", "62.4 kg", "10月20日 07:28 · 已 1 天"),
        ),
        primary = "开始测量",
    )
    ErrorStateKind.FullscreenLoad -> ErrorStateModel(
        kind = kind,
        showNav = false,
        centered = true,
        tone = CenterTone.Spinner,
        heading = "正在加载你的健康数据…",
        body = "首次登录需同步全部记录。",
        footnote = "已加载 128 / 128 条",
        secondary = "完成 →",
    )
    ErrorStateKind.LongPress -> ErrorStateModel(
        kind = kind,
        blocks = listOf(
            StateBlock.LongPress(
                menuLabel = "长按弹出菜单",
                body = "体脂率是脂肪重量占体重的比例，比单看体重更能反映身体成分。",
                highlight = "体脂率",
                actions = listOf("复制", "分享", "全选"),
                expandLabel = "长文展开 / 收起",
                collapsed = "林悦，近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化，建议持续关注 7 天平均值…",
                expanded = "林悦，近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化，建议持续关注 7 天平均值。",
            ),
        ),
        primary = "返回",
    )
}

fun errorStatePlainText(kind: ErrorStateKind): String {
    val model = errorStateModel(kind)
    return buildString {
        append(model.kind.navTitle)
        append('\n')
        model.aboveNav?.let { append(it.title).append(it.body).append(it.time) }
        append(model.heading.orEmpty())
        append(model.body.orEmpty())
        append(model.footnote.orEmpty())
        append(model.primary.orEmpty())
        append(model.secondary.orEmpty())
        model.blocks.forEach { append(it.plain()) }
    }
}

private fun StateBlock.plain(): String = when (this) {
    is StateBlock.Banner -> text + (link ?: "")
    is StateBlock.Note -> text
    is StateBlock.Locked -> label + value
    is StateBlock.Fields -> timeLabel + timeValue + noteLabel + note
    is StateBlock.FileCard -> name + meta + tag
    is StateBlock.FailCard -> title + detail + retry
    is StateBlock.Section -> title + (more ?: "")
    is StateBlock.Rows -> items.joinToString { it.title + (it.detail ?: "") + (it.trailing ?: "") }
    is StateBlock.Score -> score + caption + bands.joinToString() + how + weights.joinToString { it.first + it.second }
    is StateBlock.Chart -> legends.joinToString()
    is StateBlock.Hero -> title + subtitle
    is StateBlock.BigValue -> label + value + caption
    is StateBlock.Bullets -> title + lines.joinToString()
    is StateBlock.Headline -> text
    is StateBlock.TagLine -> tag + caption
    is StateBlock.LongPress -> menuLabel + body + highlight + actions.joinToString() + expandLabel + collapsed + expanded
}

fun boundaryPlainText(kind: ErrorDialogKind): String = when (kind) {
    ErrorDialogKind.DeleteFail -> "删除失败云端删除超时，该记录仍保留。稍后重试删除"
    ErrorDialogKind.DeviceBusy -> "设备正被其他手机使用轻衡 S1 一次只能连接一台手机。知道了重新搜索"
    ErrorDialogKind.ClearChat -> "对话管理删除这段对话删除后不可恢复清空全部对话23 段，健康数据保留"
    ErrorDialogKind.ShareReport -> "分享身体报告分享图将包含姓名与测量数据，请确认。微信保存图片复制链接取消"
}

@Composable
fun ErrorStatePage(
    kind: ErrorStateKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPrimary: () -> Unit = onBack,
    onSecondary: () -> Unit = onBack,
    onInline: (String) -> Unit = {},
) {
    val model = errorStateModel(kind)
    Column(modifier = modifier.fillMaxSize().background(QkColor.Bg)) {
        model.aboveNav?.let { NotifStrip(it) }
        if (model.showNav) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkBackButton(onClick = onBack)
                QkPageTitle(model.kind.navTitle, modifier = Modifier.padding(start = QkSpacing.Md))
            }
        }
        if (model.centered) {
            CenteredState(model, onPrimary, onSecondary, Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QkSpacing.Page),
                verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
            ) {
                model.blocks.forEach { StateBlockView(it, onInline) }
                ActionButtons(model, onPrimary, onSecondary)
                Spacer(Modifier.height(QkSpacing.Xl))
            }
        }
    }
}

@Composable
private fun CenteredState(
    model: ErrorStateModel,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (model.tone) {
            CenterTone.Spinner -> CircularProgressIndicator(color = QkColor.Primary, trackColor = QkColor.Mint)
            CenterTone.Danger -> QkIconBubble(QkIcons.Warning, QkGlyphTone.Orange, contentDescription = model.heading)
            CenterTone.Warn -> QkIconBubble(QkIcons.Warning, QkGlyphTone.Orange, contentDescription = model.heading)
            CenterTone.None -> Unit
        }
        model.heading?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
        }
        model.body?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        model.footnote?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ActionButtons(model, onPrimary, onSecondary, Modifier.padding(top = QkSpacing.Lg))
    }
}

@Composable
private fun ActionButtons(
    model: ErrorStateModel,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model.primary == null && model.secondary == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        model.primary?.let {
            QkButton(text = it, style = model.primaryStyle, modifier = Modifier.fillMaxWidth(), onClick = onPrimary)
        }
        model.secondary?.let {
            QkButton(text = it, style = QkButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(), onClick = onSecondary)
        }
    }
}

@Composable
private fun NotifStrip(nav: AboveNav) {
    QkCard(modifier = Modifier.padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkIconBubble(QkIcons.Bell, QkGlyphTone.Green, contentDescription = nav.title)
            Column(modifier = Modifier.weight(1f)) {
                Text(nav.title, style = MaterialTheme.typography.labelMedium, color = QkColor.Fg)
                Text(nav.body, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
            }
            Text(nav.time, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        }
    }
}

@Composable
private fun StateBlockView(block: StateBlock, onInline: (String) -> Unit) {
    when (block) {
        is StateBlock.Banner -> BannerView(block, onInline)
        is StateBlock.Note -> Text(block.text, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        is StateBlock.Locked -> QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.label, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(block.value, color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
            }
        }
        is StateBlock.Fields -> FieldsView(block)
        is StateBlock.FileCard -> QkCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
                QkIconBubble(QkIcons.Agreement, contentDescription = block.name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(block.name, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                    Text(block.meta, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                }
                QkPill(block.tag, QkPillTone.Green)
            }
        }
        is StateBlock.FailCard -> QkCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
                QkIcon(QkIcons.Warning, tint = QkColor.Danger, size = QkSpacing.Lg)
                Column(modifier = Modifier.weight(1f)) {
                    Text(block.title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                    Text(block.detail, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                }
                Text(
                    block.retry,
                    color = QkColor.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { onInline(block.retry) },
                )
            }
        }
        is StateBlock.Section -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(block.title, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg, modifier = Modifier.weight(1f))
            block.more?.let { Text(it, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall) }
        }
        is StateBlock.Rows -> QkCard {
            block.items.forEachIndexed { index, row ->
                QkListRow(
                    title = row.title,
                    subtitle = row.detail,
                    glyph = if (block.numbered) "${index + 1}" else null,
                    tone = row.tone,
                    showDivider = index != block.items.lastIndex,
                    trailing = row.trailing?.let { label ->
                        {
                            Text(
                                label,
                                color = QkColor.Primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { onInline(label) },
                            )
                        }
                    },
                )
            }
        }
        is StateBlock.Score -> ScoreView(block)
        is StateBlock.Chart -> ChartView(block)
        is StateBlock.Hero -> Column {
            Text(block.title, style = MaterialTheme.typography.headlineSmall, color = QkColor.Fg)
            Text(block.subtitle, color = QkColor.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        is StateBlock.BigValue -> QkCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(block.label, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
                Text(block.value, style = MaterialTheme.typography.displaySmall, color = QkColor.Fg)
                Text(block.caption, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        is StateBlock.Bullets -> QkCard {
            Text(block.title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            block.lines.forEach { Text("· $it", color = QkColor.Muted, style = MaterialTheme.typography.bodySmall) }
        }
        is StateBlock.Headline -> Text(block.text, style = MaterialTheme.typography.titleLarge, color = QkColor.Fg)
        is StateBlock.TagLine -> QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkPill(block.tag, QkPillTone.Gray)
                Spacer(Modifier.weight(1f))
                Text(block.caption, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        is StateBlock.LongPress -> LongPressView(block)
    }
}

@Composable
private fun BannerView(block: StateBlock.Banner, onInline: (String) -> Unit) {
    val bg = if (block.warn) QkColor.AccentSoft else QkColor.Mint
    val fg = if (block.warn) QkColor.AmberInk else QkColor.PrimaryDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(bg)
            .padding(QkSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        QkIcon(if (block.warn) QkIcons.Warning else QkIcons.About, tint = fg, size = QkSpacing.Lg)
        Column {
            Text(block.text, color = fg, style = MaterialTheme.typography.bodySmall)
            block.link?.let {
                Text(
                    it,
                    color = QkColor.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { onInline(it) },
                )
            }
        }
    }
}

@Composable
private fun FieldsView(block: StateBlock.Fields) {
    var time by remember { mutableStateOf(block.timeValue) }
    var note by remember { mutableStateOf(block.note) }
    QkCard {
        QkTextField(value = time, onValueChange = { time = it }, label = block.timeLabel)
        Spacer(Modifier.height(QkSpacing.Md))
        QkTextField(value = note, onValueChange = { note = it }, label = block.noteLabel, singleLine = false)
    }
}

@Composable
private fun ScoreView(block: StateBlock.Score) {
    QkCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(block.score, style = MaterialTheme.typography.displayLarge, color = QkColor.Primary)
            Text(block.caption, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs),
            ) {
                block.bands.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(QkSpacing.Sm)
                            .clip(RoundedCornerShape(QkRadius.SegmentItem))
                            .background(if (index == 1) QkColor.Primary else QkColor.ScoreTrack),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xs)) {
                block.bands.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        color = QkColor.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    QkCard {
        Text(block.how, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        block.weights.forEach { (name, pct) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(QkSpacing.Sm)
                        .clip(CircleShape)
                        .background(QkColor.Primary),
                )
                Text(name, modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm), color = QkColor.Fg, style = MaterialTheme.typography.bodySmall)
                Text(pct, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChartView(block: StateBlock.Chart) {
    if (block.legends.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Lg)) {
            block.legends.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs)) {
                    Box(
                        Modifier
                            .size(width = QkSpacing.Md, height = QkSpacing.Xs)
                            .background(if (index == 0) QkColor.Primary else QkColor.AmberInk),
                    )
                    Text(label, color = if (index == 0) QkColor.Fg else QkColor.AmberInk, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    QkCard {
        Canvas(Modifier.fillMaxWidth().height(QkSpacing.Xl * 5)) {
            val grid = QkColor.Border
            repeat(3) { i ->
                val y = size.height * (i + 1) / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            when (block.mode) {
                TrendChartMode.AnomalyGap -> {
                    drawLine(
                        QkColor.Primary,
                        Offset(0f, size.height * 0.32f),
                        Offset(size.width * 0.32f, size.height * 0.4f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        QkColor.Primary,
                        Offset(size.width * 0.48f, size.height * 0.36f),
                        Offset(size.width, size.height * 0.55f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        QkColor.ScoreTrack,
                        Offset(size.width * 0.32f, 0f),
                        Offset(size.width * 0.48f, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                    val gap = Offset(size.width * 0.32f, size.height * 0.4f)
                    drawCircle(QkColor.Card, 5.dp.toPx(), gap)
                    drawCircle(QkColor.Danger, 5.dp.toPx(), gap, style = Stroke(width = 2.dp.toPx()))
                    val ok = Offset(size.width * 0.72f, size.height * 0.46f)
                    drawCircle(QkColor.Card, 5.dp.toPx(), ok)
                    drawCircle(QkColor.Primary, 5.dp.toPx(), ok, style = Stroke(width = 2.dp.toPx()))
                }
                TrendChartMode.WeightAndFat -> {
                    drawLine(
                        QkColor.Primary,
                        Offset(0f, size.height * 0.3f),
                        Offset(size.width, size.height * 0.52f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        QkColor.AmberInk,
                        Offset(0f, size.height * 0.42f),
                        Offset(size.width, size.height * 0.68f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LongPressView(block: StateBlock.LongPress) {
    var menu by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    Text(block.menuLabel, color = QkColor.FieldLabel, style = MaterialTheme.typography.labelMedium)
    QkCard {
        QkLongPressSelectable(
            text = block.body,
            highlight = block.highlight,
            menuVisible = menu,
            onLongPress = { menu = true },
            onAction = { menu = it == QkLongPressAction.SelectAll },
        )
    }
    Text(block.expandLabel, color = QkColor.FieldLabel, style = MaterialTheme.typography.labelMedium)
    QkCard {
        QkExpandableText(
            collapsed = block.collapsed,
            expanded = block.expanded,
            expandedNow = expanded,
            onToggle = { expanded = !expanded },
        )
    }
}

@Composable
private fun BoundaryConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        QkCard {
            QkIconBubble(QkIcons.Warning, QkGlyphTone.Orange, contentDescription = title)
            Text(title, modifier = Modifier.padding(top = QkSpacing.Sm), style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(body, modifier = Modifier.padding(top = QkSpacing.Sm), color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
            ) {
                QkButton(text = dismissText, style = QkButtonStyle.Secondary, modifier = Modifier.weight(1f), onClick = onDismiss)
                QkButton(text = confirmText, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
fun DeleteRecordFailDialog(onRetry: () -> Unit, onLater: () -> Unit) {
    BoundaryConfirmDialog("删除失败", "云端删除超时，该记录仍保留。", "重试删除", "稍后", onRetry, onLater)
}

@Composable
fun DeviceBusyDialog(onSearch: () -> Unit, onDismiss: () -> Unit) {
    BoundaryConfirmDialog(
        "设备正被其他手机使用",
        "轻衡 S1 一次只能连接一台手机。",
        "重新搜索",
        "知道了",
        onSearch,
        onDismiss,
    )
}

@Composable
fun ClearChatSheet(onDeleteOne: () -> Unit, onClearAll: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        QkCard {
            Text("对话管理", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(
                "删除这段对话",
                modifier = Modifier.padding(top = QkSpacing.Md).clickable(onClick = onDeleteOne),
                color = QkColor.Danger,
                style = MaterialTheme.typography.titleSmall,
            )
            Text("删除后不可恢复", color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            Text(
                "清空全部对话",
                modifier = Modifier.padding(top = QkSpacing.Md).clickable(onClick = onClearAll),
                color = QkColor.Danger,
                style = MaterialTheme.typography.titleSmall,
            )
            Text("23 段，健康数据保留", color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ShareReportSheet(
    onWeChat: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        QkCard {
            Text("分享身体报告", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(
                "分享图将包含姓名与测量数据，请确认。",
                modifier = Modifier.padding(top = QkSpacing.Sm),
                color = QkColor.AmberInk,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ShareAction("微信", onWeChat)
                ShareAction("保存图片", onSave)
                ShareAction("复制链接", onCopy)
            }
            QkButton(text = "取消", style = QkButtonStyle.Secondary, modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md), onClick = onDismiss)
        }
    }
}

@Composable
private fun ShareAction(label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        QkIconBubble(QkIcons.Share, contentDescription = label)
        Text(label, color = QkColor.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview
@Composable
private fun ErrorStatePairPreview() {
    QkTheme { ErrorStatePage(ErrorStateKind.PairTimeout, onBack = {}) }
}

@Preview
@Composable
private fun ErrorStateEditPreview() {
    QkTheme { ErrorStatePage(ErrorStateKind.RecEdit, onBack = {}) }
}
