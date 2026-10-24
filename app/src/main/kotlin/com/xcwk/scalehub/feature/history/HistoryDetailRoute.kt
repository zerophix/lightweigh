package com.xcwk.scalehub.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.query.MeasurementReport
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.ui.states.QkInlineLoading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * history — 测量详情接线层（T-081 两屏的宿主）。
 *
 * ## 为什么单列一份
 * [HistoryDetailScreen] / [RecordEditScreen] 是呈现式组件：不持有 ViewModel、不读仓库。
 * 本文件把「历史列表点了哪一条」翻译成一次真实取数（[ReportQuery]，与报告页同源），
 * 并把结果映射成 [HistoryDetailUi]。列表 → 详情 → 报告 / 编辑 的层级因此只有一处装配点，
 * 不改 shell 的导航状态。
 *
 * ## 数据口径（不造数）
 * 详情全部字段取自同一条记录（同一个 `recordId` = 测量时间戳，AC-HT3 同源约束）：
 * 体重取记录原值，体脂率 / 肌肉重量 / 评分取 [BodyAnalysisResult] 的同名指标，
 * 缺项按 `--` 呈现（同报告页口径）。
 *
 * ## 已知限制
 * 数据层没有「更新已有测量」的端口（`MeasurementRepository` 只有 save / delete，
 * [com.xcwk.scalehub.domain.model.MeasureRecord] 的时间戳是主键），所以编辑页
 * 只呈现与校验、保存按钮置灰并说明原因——不伪造一次「已保存」。
 */

/** 详情页取数结果：加载中 / 无此记录 / 已就绪。 */
sealed interface HistoryDetailLoad {
    data object Loading : HistoryDetailLoad
    data object Missing : HistoryDetailLoad
    data class Ready(val page: HistoryDetailPage) : HistoryDetailLoad
}

/** 详情页一次快照 + 编辑页需要的 BMI 文案（同一记录，避免编辑页另取一次数）。 */
data class HistoryDetailPage(
    val detail: HistoryDetailUi,
    val bmiText: String,
)

/** 编辑页的保存限制说明（数据层无更新接口时的显式出口，不做静默丢改动）。 */
const val RECORD_EDIT_UNSUPPORTED_NOTICE: String =
    "本机暂不支持修改已有记录：数据层没有更新接口，请以体脂秤实测记录为准。"

/** 记录 → 详情快照（纯函数，JVM 单测覆盖字段映射与缺项口径）。 */
fun historyDetailUiOf(report: MeasurementReport, timeText: String): HistoryDetailUi {
    val metric = { name: String -> report.analysis.metrics.firstOrNull { it.name == name } }
    val fat = metric("体脂率")
    val muscle = metric("肌肉重量")
    return HistoryDetailUi(
        recordId = report.record.time,
        metaText = timeText,
        weightValue = formatOne(report.record.weight),
        weightDelta = "",
        fatRateValue = fat?.valueText ?: "--",
        fatRateDelta = fat?.delta.orEmpty(),
        muscleValue = muscle?.valueText ?: "--",
        muscleDelta = muscle?.delta.orEmpty(),
        scoreValue = report.analysis.score?.toString() ?: "--",
        // 同步状态没有独立数据源（全库本地优先），不额外造「本地/已同步」标签
        synced = true,
    )
}

/** 编辑页模型：测量字段只读呈现（来自设备），备注从详情页带入。 */
fun recordEditModelOf(page: HistoryDetailPage, note: String): RecordEditModel = RecordEditModel(
    recordId = page.detail.recordId,
    timeText = page.detail.metaText,
    note = note,
    weightText = "${page.detail.weightValue} ${page.detail.weightUnit}",
    fatRateText = "${page.detail.fatRateValue} %",
    muscleText = "${page.detail.muscleValue} kg",
    bmiText = page.bmiText,
)

private fun formatOne(value: Double): String = "%.1f".format(Locale.US, value)

/**
 * 详情页装配点：按 [time] 取报告 → 详情；详情里「编辑」进编辑页，「查看指标详情」交宿主打开报告页。
 *
 * @param onBack 返回历史列表
 * @param onOpenReport 打开该次测量的完整体测报告（与详情同源：同一个 [time]）
 */
@Composable
fun HistoryDetailRoute(
    time: Long,
    onBack: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    vm: HistoryDetailViewModel = viewModel(factory = HistoryDetailViewModel.Factory()),
) {
    val load by vm.load.collectAsStateWithLifecycle()
    LaunchedEffect(time) { vm.load(time) }

    val page = (load as? HistoryDetailLoad.Ready)?.page
    var editing by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }

    when {
        page == null && load is HistoryDetailLoad.Loading ->
            QkInlineLoading(label = "正在读取测量详情…")

        page == null -> HistoryDetailEmpty(onBack = onBack)

        editing -> RecordEditScreen(
            model = recordEditModelOf(page, note),
            onBack = { editing = false },
            onNoteChange = { note = it },
            // 数据层无更新端口：保存置灰并给出原因，不改内存态冒充已保存
            saveViolations = listOf(RECORD_EDIT_UNSUPPORTED_NOTICE),
        )

        else -> HistoryDetailScreen(
            detail = page.detail,
            onBack = onBack,
            onEdit = { editing = true },
            onOpenMetricDetail = { onOpenReport(time) },
        )
    }
}

/** 详情页取数 VM：只依赖 [ReportQuery]（与报告页同一数据源）。 */
class HistoryDetailViewModel(
    private val reportQuery: ReportQuery,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _load = MutableStateFlow<HistoryDetailLoad>(HistoryDetailLoad.Loading)
    val load: StateFlow<HistoryDetailLoad> = _load.asStateFlow()

    fun load(time: Long) {
        viewModelScope.launch {
            val report = runCatching { reportQuery.report(time) }.getOrNull()
            _load.value = if (report == null) {
                HistoryDetailLoad.Missing
            } else {
                val detail = historyDetailUiOf(report, timeTextOf(time))
                val bmi = report.analysis.metrics.firstOrNull { it.name == "BMI" }?.valueText ?: "--"
                HistoryDetailLoad.Ready(HistoryDetailPage(detail, bmi))
            }
        }
    }

    private fun timeTextOf(time: Long): String =
        DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(zone).format(Instant.ofEpochMilli(time))

    companion object {
        fun Factory(zone: ZoneId = ZoneId.systemDefault()) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                HistoryDetailViewModel(app.container.reportQuery, zone)
            }
        }
    }
}
