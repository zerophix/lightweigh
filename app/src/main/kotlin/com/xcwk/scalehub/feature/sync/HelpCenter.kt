package com.xcwk.scalehub.feature.sync

import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * help-center — 帮助中心与 FAQ 详情（T-093 · 原型08 v-help / v-faq）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/08-sync-network-help.html`
 * - 验收：AC-N3「帮助中心与反馈流程」then 第 1/2 条，文案逐字取自原型
 *
 * ## 呈现式约束（AC-N3 forbidden 原文）
 * 1. FAQ 列表经 [HelpCenterScreen] 的 [faqs] 参数注入，本文件不拉取远端 FAQ；
 * 2. 打开某条 FAQ 经 [HelpCenterScreen.onOpenFaq] 回调交给调用方，
 *    进入详情由调用方决定（本文件不持有导航栈）；
 * 3. 颜色/间距一律 Qk token，无硬编码色值与裸 dp。
 *
 * ## 数据口径
 * [HELP_FAQ_ITEMS] / [WEIGHT_FLUCTUATION_FAQ] 是原型示例数据，供宿主无数据源时兜底；
 * 真实数据源接入后由调用方传入同构对象替换。
 */

/** 原型 v-help / v-faq 文案（逐字，勿改）。 */
object HelpCenterText {
    // v-help
    const val TopBarTitle = "帮助与反馈"
    const val SearchHint = "搜索问题，如「测量不准」"
    const val FaqSectionTitle = "常见问题"
    const val DiagnosticsEntryTitle = "上传诊断日志"
    const val DiagnosticsEntryDesc = "帮助排查问题"
    const val FeedbackEntry = "意见反馈"

    // v-faq
    const val FaqTopBarTitle = "测量结果有波动"
    const val FaqQuestion = "为什么同一天测量结果会有波动？"
    const val FaqBody = "体重一天内自然波动 1–2 kg 是正常的，来自水分、进食和排泄。"
    const val FaqTipsTitle = "如何减少波动："
    const val FaqSummary = "建议关注 7 天或 30 天平均趋势，而不是单次数值。"
    const val FaqBanner = "体脂秤为估算设备，数据仅供参考。"
    const val FaqUnsolved = "没解决"
    const val FaqSolved = "解决了"
}

/** v-faq 「如何减少波动」三条要点（原型逐字，含行首圆点）。 */
val FAQ_TIPS = listOf(
    "· 每天同一时段测量",
    "· 测量前避免大量饮水或进食",
    "· 保持测量条件一致",
)

/** FAQ 列表项：只携带列表呈现所需信息，详情正文另由 [FaqDetail] 提供。 */
data class FaqItem(val id: String, val question: String)

/**
 * FAQ 详情：标题、问题、两段正文、三条要点与提示横幅全部由调用方注入。
 * [topBarTitle] 是导航条标题，与 [question] 分开（原型两者文案不同）。
 */
data class FaqDetail(
    val id: String,
    val topBarTitle: String,
    val question: String,
    val body: String,
    val tipsTitle: String,
    val tips: List<String>,
    val summary: String,
    val banner: String,
)

/** FAQ 评价：详情页底部两态（原型「没解决」「解决了」）。 */
enum class FaqVerdict(val label: String) {
    Unsolved(HelpCenterText.FaqUnsolved),
    Solved(HelpCenterText.FaqSolved),
}

/** 原型 v-help 的三条常见问题（示例数据，可被调用方替换）。 */
val HELP_FAQ_ITEMS = listOf(
    FaqItem(id = "weight-fluctuation", question = "为什么测量结果有波动？"),
    FaqItem(id = "device-connection", question = "设备连接失败怎么办？"),
    FaqItem(id = "sync-failure", question = "数据没有同步"),
)

/** 原型 v-faq 的详情内容（示例数据）。原型三条 FAQ 均指向这一屏。 */
val WEIGHT_FLUCTUATION_FAQ = FaqDetail(
    id = "weight-fluctuation",
    topBarTitle = HelpCenterText.FaqTopBarTitle,
    question = HelpCenterText.FaqQuestion,
    body = HelpCenterText.FaqBody,
    tipsTitle = HelpCenterText.FaqTipsTitle,
    tips = FAQ_TIPS,
    summary = HelpCenterText.FaqSummary,
    banner = HelpCenterText.FaqBanner,
)

// ---- v-help / v-faq 共享原语 ----

/** 压栈页导航条：返回圆钮 + 页标题（原型 .navbar）。 */
@Composable
internal fun HelpNavBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    QkScreenHeader(title, onBack, modifier = modifier)
}

/** 提示横幅（原型 .banner：薄荷底圆角条 + 左侧线性图标 + 小字）。 */
@Composable
internal fun HelpBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = QkColor.Primary, size = QkSpacing.Lg)
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** 可点击的列表行（原型 .row.center：主副文案 + 右侧箭头）。 */
@Composable
internal fun HelpLinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }
        QkIcon(QkIcons.ChevronRight, tint = QkColor.Muted, size = QkSpacing.Lg)
    }
}

// ---- v-help 帮助中心 ----

/**
 * 帮助中心（原型 v-help）。
 *
 * 搜索框为受控输入（[searchQuery] + [onSearchChange]）；输入过滤由调用方完成，
 * 本页只负责呈现，避免把检索规则藏进 UI。
 */
@Composable
fun HelpCenterScreen(
    faqs: List<FaqItem>,
    onBack: () -> Unit,
    onOpenFaq: (FaqItem) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        HelpNavBar(title = HelpCenterText.TopBarTitle, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = HelpCenterText.SearchHint,
            )
            Text(
                HelpCenterText.FaqSectionTitle,
                modifier = Modifier.padding(start = QkSpacing.Xs, top = QkSpacing.Xs),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            QkCard(padding = QkSpacing.Sm) {
                faqs.forEach { faq ->
                    HelpLinkRow(
                        title = faq.question,
                        onClick = { onOpenFaq(faq) },
                    )
                }
            }
            QkCard(padding = QkSpacing.Sm) {
                HelpLinkRow(
                    title = HelpCenterText.DiagnosticsEntryTitle,
                    subtitle = HelpCenterText.DiagnosticsEntryDesc,
                    onClick = onOpenDiagnostics,
                )
            }
        }
        Spacer(Modifier.height(QkSpacing.Xs))
        QkButton(
            text = HelpCenterText.FeedbackEntry,
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            onClick = onOpenFeedback,
        )
    }
}

// ---- v-faq FAQ 详情 ----

/**
 * FAQ 详情（原型 v-faq）：问题 + 两段正文 + 要点清单 + 提示横幅 + 底部评价。
 *
 * [onVerdict] 上报「没解决 / 解决了」；上报后回到列表由调用方处理（原型即返回）。
 */
@Composable
fun FaqDetailScreen(
    detail: FaqDetail,
    onBack: () -> Unit,
    onVerdict: (FaqVerdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        HelpNavBar(title = detail.topBarTitle, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            Text(
                detail.question,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                detail.body,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
            Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
                Text(
                    detail.tipsTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Fg,
                )
                detail.tips.forEach { tip ->
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Sub,
                    )
                }
            }
            Text(
                detail.summary,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
            HelpBanner(icon = QkIcons.About, text = detail.banner)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = HelpCenterText.FaqUnsolved,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = { onVerdict(FaqVerdict.Unsolved) },
            )
            QkButton(
                text = HelpCenterText.FaqSolved,
                modifier = Modifier.weight(1f),
                onClick = { onVerdict(FaqVerdict.Solved) },
            )
        }
    }
}
