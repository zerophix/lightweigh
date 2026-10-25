package com.xcwk.scalehub.feature.measure

import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * measure — 测量结果的分享与导出（T-078 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/16-measure-result.html` 的三个 view：
 * - `v-share` 分享预览：数据安全警示 + 报告分享卡 + 四个分享渠道 + 取消
 * - `v-export` 导出配置：格式三选一 + 范围三项 + 包含字段 + 加密链接提示
 * - `v-export-done` 导出成功：邮箱加密链接 + 24 小时有效期 + 返回/重发
 *
 * ## 验收（AC-J1-06 测量结果可分享/导出）
 * - 分享预览含警示、分享卡（品牌/姓名日期/体重/较上次/四项指标/页脚）与
 *   「微信 / 朋友圈 / 保存图片 / 复制链接」四渠道
 * - 导出页含「CSV / PDF / 图片」三格式、「本次测量 / 近 30 天 / 全部记录」三范围、
 *   十项字段与「生成导出文件」「取消」
 * - 导出成功页含邮箱与 24 小时有效期文案，以及「返回报告」「重新发送到邮箱」
 *
 * ## 边界（呈现式组件）
 * 本文件只渲染传入状态 + 回调，**不**调用分享 SDK、文件系统、网络或数据库：
 * - 选渠道 → [ResultShareFlow] 的 `onShare`
 * - 生成导出 → `onExport`（[ExportRequest] 已由纯函数校验可用性）
 * - 重新发送 → `onResend`
 * - 返回 → `onBack` / `onBackToReport`
 *
 * 真正的分享/导出/发信归宿主 ViewModel 与既有 measure 流程；本任务不重写报告主体。
 *
 * ## 尺寸口径
 * 本文件不出现裸 `N.dp`：通道图标块 52dp 由 [QkSpacing] 组合得到，
 * 圆角取 [QkRadius]，字号取 [MaterialTheme.typography]。
 */

// ---- 三态状态机 ----

/** 分享与导出的三个呈现态（与原型三个 view 一一对应）。 */
enum class ResultShareStep { Share, Export, ExportDone }

/** 原型 v-share 的四个分享渠道。 */
enum class ShareChannel(val label: String, val notice: String) {
    Wechat("微信", "已分享到微信"),
    Moments("朋友圈", "已分享到朋友圈"),
    SaveImage("保存图片", "已保存到相册"),
    CopyLink("复制链接", "已复制链接"),
}

/** 导出文件格式（原型 v-export 分段控件三项）。 */
enum class ExportFormat(val label: String) {
    Csv("CSV"),
    Pdf("PDF"),
    Image("图片"),
    ;

    /**
     * 该格式可承载的字段。
     *
     * 口径：CSV / PDF 是完整数据导出，十项字段全带；「图片」是报告分享图，
     * 图上只有分享卡那四项指标（评分 / 体脂 / BMI / 肌肉量），
     * 因此水分率、内脏脂肪等明细不出现在图片里。
     */
    val supportedFields: List<ExportField>
        get() = when (this) {
            Csv, Pdf -> ExportField.entries
            Image -> listOf(ExportField.BodyScore, ExportField.BodyFatRate, ExportField.Bmi, ExportField.MuscleMass)
        }
}

/** 导出范围（原型 v-export 三个单选行；副标题是原型示例数据）。 */
enum class ExportRange(val title: String, val subtitle: String) {
    Current("本次测量", "10月21日 07:30"),
    Last30Days("近 30 天", "09/21 — 10/21 · 20 次"),
    All("全部记录", "128 次"),
}

/** 导出字段（原型 v-export「包含字段」两行、每行五项，顺序即原型顺序）。 */
enum class ExportField(val label: String) {
    MeasuredAt("测量时间"),
    Weight("体重"),
    BodyFatRate("体脂率"),
    Bmi("BMI"),
    MuscleMass("肌肉量"),
    WaterRate("水分率"),
    VisceralFat("内脏脂肪"),
    BasalMetabolism("基础代谢"),
    BoneMass("骨量"),
    BodyScore("身体评分"),
}

/** 导出可用性判定结果；非 [Available] 时「生成导出文件」置灰。 */
enum class ExportAvailability {
    Available,

    /** 一个字段都没选：空文件没有意义 */
    NoFieldSelected,

    /** 所选字段里有该格式承载不了的项（如图片格式带水分率） */
    FieldNotSupported,

    /** 图片格式只输出单次报告，不能跨多次记录 */
    RangeUnsupportedForImage,
}

/** 一次导出请求（真正落文件/发信的输入，由宿主消费）。 */
data class ExportRequest(
    val format: ExportFormat,
    val range: ExportRange,
    val fields: List<ExportField>,
    val email: String,
)

/**
 * 呈现式 UI 状态。
 *
 * [shareCard] 与 [email] 由宿主注入真实用户数据；默认值只是原型示例，
 * 便于设计走查与截图比对。
 */
data class ResultShareUiState(
    val step: ResultShareStep = ResultShareStep.Share,
    val format: ExportFormat = ExportFormat.Csv,
    val range: ExportRange = ExportRange.Current,
    val fields: List<ExportField> = ExportField.entries,
    val email: String = SAMPLE_EXPORT_EMAIL,
    val shareCard: ShareCardData = ShareCardData.sample(),
    /** 最近一次选中的分享渠道（宿主用来展示 toast/埋点前先自查） */
    val lastShareChannel: ShareChannel? = null,
)

/** 流程内意图。副作用一律走回调，不进 [resultShareReduce]。 */
sealed interface ResultShareIntent {
    data class SelectChannel(val channel: ShareChannel) : ResultShareIntent
    data object OpenExport : ResultShareIntent
    data class SelectFormat(val format: ExportFormat) : ResultShareIntent
    data class SelectRange(val range: ExportRange) : ResultShareIntent
    data class SetFields(val fields: List<ExportField>) : ResultShareIntent
    data object GenerateExport : ResultShareIntent
}

/**
 * 纯状态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - [ResultShareIntent.SelectFormat] 切格式时把字段收敛到该格式支持的范围，
 *   避免「选了图片却还留着水分率」这种不可导出的组合；
 * - [ResultShareIntent.GenerateExport] 只在 [exportAvailability] 为
 *   [ExportAvailability.Available] 时进入成功页，否则原地不动（按钮本就是灰的，
 *   这里是防御第二道）。
 * - 返回/取消不改状态：它们是宿主导航，不属于流程内部转移。
 */
fun resultShareReduce(state: ResultShareUiState, intent: ResultShareIntent): ResultShareUiState = when (intent) {
    is ResultShareIntent.SelectChannel -> state.copy(lastShareChannel = intent.channel)
    ResultShareIntent.OpenExport -> state.copy(step = ResultShareStep.Export)
    is ResultShareIntent.SelectFormat -> state.copy(
        format = intent.format,
        fields = state.fields.filter { it in intent.format.supportedFields },
    )

    is ResultShareIntent.SelectRange -> state.copy(range = intent.range)
    is ResultShareIntent.SetFields -> state.copy(fields = intent.fields)
    ResultShareIntent.GenerateExport -> if (exportAvailability(state.format, state.range, state.fields) ==
        ExportAvailability.Available
    ) {
        state.copy(step = ResultShareStep.ExportDone)
    } else {
        state
    }
}

/**
 * 导出可用性：格式 × 范围 × 字段组合三者的交叉判定。
 *
 * 判定顺序：先看字段是否为空，再看格式是否支持这些字段，最后看图片格式的范围限制。
 * 顺序固定，是为了让同一种组合永远只报一个原因（宿主可直接拿原因做提示）。
 */
fun exportAvailability(
    format: ExportFormat,
    range: ExportRange,
    fields: List<ExportField>,
): ExportAvailability = when {
    fields.isEmpty() -> ExportAvailability.NoFieldSelected
    !format.supportedFields.containsAll(fields) -> ExportAvailability.FieldNotSupported
    format == ExportFormat.Image && range != ExportRange.Current -> ExportAvailability.RangeUnsupportedForImage
    else -> ExportAvailability.Available
}

/** 可用则产出请求，不可用返回 null（按钮 enabled 与回调参数共用同一判定）。 */
fun buildExportRequest(state: ResultShareUiState): ExportRequest? =
    if (exportAvailability(state.format, state.range, state.fields) == ExportAvailability.Available) {
        ExportRequest(
            format = state.format,
            range = state.range,
            fields = state.fields,
            email = state.email,
        )
    } else {
        null
    }

// ---- 静态文案（逐字取自原型，JVM 单测校验） ----

const val SHARE_TITLE = "分享报告"
const val SHARE_WARNING = "分享图片将包含你的姓名与测量数据，请确认后再分享。"
const val SHARE_TO_LABEL = "分享到"
const val CANCEL = "取消"

const val EXPORT_TITLE = "导出报告"
const val EXPORT_FORMAT_LABEL = "导出格式"
const val EXPORT_RANGE_LABEL = "导出范围"
const val EXPORT_FIELDS_LABEL = "包含字段"
const val EXPORT_BANNER = "导出文件通过加密链接发送到邮箱，24 小时内有效。"
const val EXPORT_GENERATE = "生成导出文件"

const val EXPORT_DONE_TITLE = "导出成功"
const val EXPORT_DONE_LINE_1 = "文件已生成，加密链接已发送至邮箱"
const val EXPORT_DONE_LINE_2 = "24 小时内有效，请尽快下载。"
const val EXPORT_DONE_BACK = "返回报告"
const val EXPORT_DONE_RESEND = "重新发送到邮箱"

/** 原型示例邮箱（宿主注入真实值覆盖）。 */
const val SAMPLE_EXPORT_EMAIL = "linyue@example.com"

/** 「包含字段」两行：按原型 5 + 5 分组，组内以「 · 」连接。 */
val EXPORT_FIELD_LINES: List<String> = ExportField.entries
    .chunked(5)
    .map { group -> group.joinToString(" · ") { it.label } }

// ---- 分享卡数据 ----

/** 分享卡上的一项指标（原型四列：评分 / 体脂 / BMI / 肌肉kg）。 */
data class ShareMetric(val value: String, val caption: String)

/**
 * 分享卡内容（品牌行 + 姓名日期 + 主体重 + 较上次胶囊 + 四项指标 + 页脚）。
 *
 * 全部字段由宿主注入；[sample] 是原型 `v-share` 的示例值（62.4 kg / ↓0.3）。
 */
data class ShareCardData(
    val brand: String,
    val headline: String,
    val weight: String,
    val weightUnit: String,
    val delta: String,
    val metrics: List<ShareMetric>,
    val footer: String,
) {
    companion object {
        fun sample(): ShareCardData = ShareCardData(
            brand = "轻衡健康 · 身体成分报告",
            headline = "林悦 · 10月21日",
            weight = "62.4",
            weightUnit = "kg",
            delta = "↓0.3 较上次",
            metrics = listOf(
                ShareMetric("86", "评分"),
                ShareMetric("28.6%", "体脂"),
                ShareMetric("22.1", "BMI"),
                ShareMetric("42.3", "肌肉kg"),
            ),
            footer = "轻衡 · 让健康轻一点",
        )
    }
}

// ---- 尺寸（只由 token 组合，不写裸 dp） ----

/** 分享渠道图标块 52dp（原型 52×52 圆角块）。 */
private val ChannelTileSize = QkSpacing.Xl * 2 + QkSpacing.Xs

/** 成功页对勾圆 56dp。 */
private val DoneBadgeSize = QkSpacing.Xl * 2 + QkSpacing.Sm

// ---- 呈现 ----

/**
 * 三态路由：按 [ResultShareUiState.step] 呈现对应 view。
 *
 * 外部交接点：
 * - [onShare] 选中某个分享渠道（微信/朋友圈/保存图片/复制链接）
 * - [onExport] 生成导出文件（参数已通过可用性校验，不会为空）
 * - [onResend] 成功页「重新发送到邮箱」
 * - [onBack] 分享/导出页返回；[onBackToReport] 成功页「返回报告」
 */
@Composable
fun ResultShareFlow(
    state: ResultShareUiState,
    onIntent: (ResultShareIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onShare: (ShareChannel) -> Unit = {},
    onExport: (ExportRequest) -> Unit = {},
    onResend: () -> Unit = {},
    onBackToReport: () -> Unit = onBack,
) {
    when (state.step) {
        ResultShareStep.Share -> ResultShareView(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onShare = onShare,
            modifier = modifier,
        )

        ResultShareStep.Export -> ResultExportView(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onExport = onExport,
            modifier = modifier,
        )

        ResultShareStep.ExportDone -> ResultExportDoneView(
            email = state.email,
            onBackToReport = onBackToReport,
            onResend = onResend,
            modifier = modifier,
        )
    }
}

/** v-share：警示条 + 分享卡预览 + 四渠道 + 取消。 */
@Composable
internal fun ResultShareView(
    state: ResultShareUiState,
    onIntent: (ResultShareIntent) -> Unit,
    onBack: () -> Unit,
    onShare: (ShareChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ShareNavBar(title = SHARE_TITLE, onBack = onBack)

        Spacer(Modifier.height(QkSpacing.Sm))
        ShareWarningBanner()
        Spacer(Modifier.height(QkSpacing.CardGap))

        ShareCardPreview(state.shareCard)

        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            SHARE_TO_LABEL,
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
        Spacer(Modifier.height(QkSpacing.Xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ShareChannel.entries.forEach { channel ->
                ShareChannelTile(
                    channel = channel,
                    onClick = {
                        onShare(channel)
                        onIntent(ResultShareIntent.SelectChannel(channel))
                    },
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = CANCEL,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            onClick = onBack,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** v-export：格式分段 + 范围单选 + 字段说明 + 加密链接提示 + 生成/取消。 */
@Composable
internal fun ResultExportView(
    state: ResultShareUiState,
    onIntent: (ResultShareIntent) -> Unit,
    onBack: () -> Unit,
    onExport: (ExportRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = buildExportRequest(state)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ShareNavBar(title = EXPORT_TITLE, onBack = onBack)

        Spacer(Modifier.height(QkSpacing.Md))
        Text(EXPORT_FORMAT_LABEL, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Spacer(Modifier.height(QkSpacing.Sm))
        QkSegmented(
            items = ExportFormat.entries.map { it.label },
            selectedIndex = ExportFormat.entries.indexOf(state.format),
            onSelect = { index -> onIntent(ResultShareIntent.SelectFormat(ExportFormat.entries[index])) },
        )

        Spacer(Modifier.height(QkSpacing.Md))
        Text(EXPORT_RANGE_LABEL, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Spacer(Modifier.height(QkSpacing.Sm))
        QkCard(padding = QkSpacing.Sm) {
            ExportRange.entries.forEachIndexed { index, range ->
                QkListRow(
                    title = range.title,
                    subtitle = range.subtitle,
                    showDivider = index < ExportRange.entries.lastIndex,
                    trailing = {
                        RadioButton(
                            selected = state.range == range,
                            onClick = { onIntent(ResultShareIntent.SelectRange(range)) },
                            colors = RadioButtonDefaults.colors(selectedColor = QkColor.Primary),
                        )
                    },
                    onClick = { onIntent(ResultShareIntent.SelectRange(range)) },
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Md))
        Text(EXPORT_FIELDS_LABEL, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Spacer(Modifier.height(QkSpacing.Sm))
        QkCard(padding = QkSpacing.Md) {
            EXPORT_FIELD_LINES.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Md))
        ShareNoticeBanner(text = EXPORT_BANNER)

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = EXPORT_GENERATE,
            modifier = Modifier.fillMaxWidth(),
            enabled = request != null,
            onClick = {
                request?.let(onExport)
                onIntent(ResultShareIntent.GenerateExport)
            },
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = CANCEL,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            onClick = onBack,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** v-export-done：对勾 + 成功标题 + 邮箱与有效期 + 返回/重发。 */
@Composable
internal fun ResultExportDoneView(
    email: String,
    onBackToReport: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(QkSpacing.Xl * 2))
        Box(
            modifier = Modifier
                .size(DoneBadgeSize)
                .clip(CircleShape)
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(
                QkIcons.Habit,
                tint = QkColor.Primary,
                size = QkSpacing.Xl,
                contentDescription = EXPORT_DONE_TITLE,
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            EXPORT_DONE_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        Text(
            EXPORT_DONE_LINE_1,
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Text(
            email,
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            EXPORT_DONE_LINE_2,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(QkSpacing.Xl * 2))
        QkButton(
            text = EXPORT_DONE_BACK,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBackToReport,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = EXPORT_DONE_RESEND,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onResend,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

// ---- 共享原语 ----

/** 压栈页导航条（原型 .navbar：返回 + 居中偏左标题）。 */
@Composable
private fun ShareNavBar(title: String, onBack: () -> Unit) {
    QkScreenHeader(title, onBack)
}

/** v-share 的数据安全警示（原型 .banner.warn：警示图标 + 说明）。 */
@Composable
private fun ShareWarningBanner(modifier: Modifier = Modifier) {
    ShareNoticeBanner(
        text = SHARE_WARNING,
        icon = QkIcons.About,
        modifier = modifier,
    )
}

/**
 * 提示条（原型 .banner）：薄荷底 + 品牌色线性图标 + 次级文案。
 *
 * 分享警示与导出加密提示共用同一容器，图标语义由调用方指定。
 */
@Composable
private fun ShareNoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = QkIcons.Privacy,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = QkColor.Primary, contentDescription = null)
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** 分享卡预览（原型 v-share 卡片：品牌渐变头 + 四指标 + 页脚）。 */
@Composable
private fun ShareCardPreview(card: ShareCardData, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(QkRadius.Card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(QkStroke.Thin, QkColor.Border, shape)
            .background(QkColor.Card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(QkColor.Primary, QkColor.PrimaryDark)))
                .padding(horizontal = QkSpacing.Lg + QkSpacing.Xs, vertical = QkSpacing.Xl),
        ) {
            Text(
                card.brand,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                card.headline,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    card.weight,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
                Text(
                    card.weightUnit,
                    modifier = Modifier.padding(start = QkSpacing.Xs),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(QkRadius.NavFab))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Xs),
                ) {
                    Text(
                        card.delta,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = QkSpacing.Lg + QkSpacing.Xs,
                vertical = QkSpacing.Lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            card.metrics.forEachIndexed { index, metric ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        metric.value,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == 0) QkColor.Primary else QkColor.Fg,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        metric.caption,
                        modifier = Modifier.padding(top = QkSpacing.Xs / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Text(
            card.footer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = QkSpacing.Md),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 分享渠道块（原型 52×52 圆角块 + 下方标签）。 */
@Composable
private fun ShareChannelTile(channel: ShareChannel, onClick: () -> Unit) {
    // 微信 / 朋友圈用品牌绿实底，保存图片 / 复制链接用薄荷底（原型配色区分）
    val highlight = channel == ShareChannel.Wechat || channel == ShareChannel.Moments
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ChannelTileSize)
                .clip(RoundedCornerShape(QkRadius.Tile))
                .background(if (highlight) QkColor.Primary else QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(
                icon = channel.icon(),
                tint = if (highlight) Color.White else QkColor.Primary,
                size = QkSpacing.Xl,
                contentDescription = channel.label,
            )
        }
        Text(
            channel.label,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
    }
}

/**
 * 渠道图标：QkIcons 表里语义最近的一项。
 *
 * QkIcons 目前没有微信/朋友圈的矢量图，按该文件的纪律（页面不直接 import material icons）
 * 从现有表取近似：分享 / 发送 / 下载 / 链接说明。
 */
private fun ShareChannel.icon(): ImageVector = when (this) {
    ShareChannel.Wechat -> QkIcons.Share
    ShareChannel.Moments -> QkIcons.Send
    ShareChannel.SaveImage -> QkIcons.Restore
    ShareChannel.CopyLink -> QkIcons.Agreement
}
