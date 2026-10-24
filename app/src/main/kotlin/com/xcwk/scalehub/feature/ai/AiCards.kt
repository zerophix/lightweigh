package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

// ---- AI 结构化卡片（DEC-043-B，参考 AiChat.kt:341-410）----
//
// 文案与交互判定一律走本文件里的纯函数 / 常量（[AiCardText] 与 [dietCardTexts] 等），
// Compose 只负责摆放：这样「四类卡渲染什么字」「示例引导点得动吗」都能被 JVM 单测
// 钉死，不必依赖真机截图。

/** 饮食卡（AiChat.kt:410+ DietCard）。 */
data class DietCard(
    val date: String,
    val meals: List<DietMeal>,
)

data class DietMeal(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

/** 运动卡。 */
data class ExerciseCard(
    val name: String,
    val duration: Int,     // 分钟
    val calories: Int,
    val heartRate: Int? = null,
)

/** 指标解读卡。 */
data class MetricCard(
    val metricName: String,
    val value: String,
    val level: String,      // 偏低/标准/偏高
    val advice: String,
    val tone: Int,          // 0低 1标准 2高
)

// ---- 一、卡片文案（Compose 与单测共用同一份字面量）----

/** 四类卡片用到的固定文案。改一个字就等于改验收，故集中在此。 */
object AiCardText {
    // 饮食卡
    const val DietTitlePrefix = "饮食记录"
    const val CaloriesUnit = "千卡"
    const val MealEdit = "编辑"
    const val MealDelete = "删除"
    const val MealSave = "保存"

    // 运动卡
    const val ExerciseHeartRateSuffix = "bpm"

    // 指标解读卡
    const val LevelLow = "偏低"
    const val LevelNormal = "标准"
    const val LevelHigh = "偏高"

    // 欢迎卡
    const val WelcomeTitle = "你好，我是你的健康助理"
    const val WelcomeIntro = "我可以帮你分析身体数据、解答健康问题、记录饮食和运动。"
    const val WelcomeExamplesHint = "试试这些问题："
    const val WelcomeExampleBullet = "· "
    val WelcomeExamples = listOf(
        "我今天的体脂率正常吗？",
        "帮我制定一个减脂计划",
        "早餐吃什么有助于减肥？",
        "如何提高基础代谢？",
    )

    // 拍照入口
    const val PhotoEntry = "拍照记录"
}

/** 饮食卡一行营养素文案（三大营养素）。 */
fun dietMacroText(meal: DietMeal): String =
    "蛋白质 ${meal.protein}g · 碳水 ${meal.carbs}g · 脂肪 ${meal.fat}g"

/** 饮食卡一行热量文案。 */
fun dietCaloriesText(meal: DietMeal): String = "${meal.calories} ${AiCardText.CaloriesUnit}"

/** 运动卡摘要（时长 / 消耗）。 */
fun exerciseSummaryText(card: ExerciseCard): String =
    "${card.duration} 分钟 · ${card.calories} ${AiCardText.CaloriesUnit}"

/** 运动卡心率（无心率数据时返回 null，不渲染该项）。 */
fun exerciseHeartRateText(card: ExerciseCard): String? =
    card.heartRate?.let { "心率 $it ${AiCardText.ExerciseHeartRateSuffix}" }

/** 饮食卡可见文案：标题 + 每餐（名称 / 营养素 / 热量）+ 三个操作按钮。 */
fun dietCardTexts(card: DietCard): List<String> {
    val header = "${AiCardText.DietTitlePrefix} ${card.date}"
    val meals = card.meals.flatMap { listOf(it.name, dietMacroText(it), dietCaloriesText(it)) }
    return listOf(header) + meals +
        listOf(AiCardText.MealEdit, AiCardText.MealDelete, AiCardText.MealSave)
}

/** 运动卡可见文案：名称 + 时长/消耗 +（可选）心率。 */
fun exerciseCardTexts(card: ExerciseCard): List<String> =
    listOf(card.name, exerciseSummaryText(card)) + listOfNotNull(exerciseHeartRateText(card))

/** 指标解读卡可见文案：指标名 + 数值 + 等级 + 解读。 */
fun metricCardTexts(card: MetricCard): List<String> =
    listOf(card.metricName, card.value, card.level, card.advice)

/** 欢迎卡可见文案：标题 + 说明 + 示例引导（四个示例问题）。 */
fun welcomeTexts(): List<String> =
    listOf(AiCardText.WelcomeTitle, AiCardText.WelcomeIntro, AiCardText.WelcomeExamplesHint) +
        AiCardText.WelcomeExamples.map { AiCardText.WelcomeExampleBullet + it }

// ---- 二、回调可用性判定（未实现时不得渲染成可点死入口）----

/**
 * 示例引导是否可点：回调为空视为未实现，此时示例只作说明文字展示。
 *
 * 修复前 [AiWelcome] 把示例问题渲染成胶囊形状却从不调用 `onExampleClick`，
 * 视觉上可点、点下去无反应，属于「可点死入口」。
 */
fun aiExampleClickAvailable(onExampleClick: ((String) -> Unit)?): Boolean = onExampleClick != null

/** 点一个示例问题；未实现返回 false 且不吞点击，已实现时恰好回调一次。 */
fun triggerAiExample(onExampleClick: ((String) -> Unit)?, question: String): Boolean {
    if (onExampleClick == null) return false
    onExampleClick(question)
    return true
}

/** 拍照入口是否可用：回调为空（相机 / 选图未接入）时不得渲染可点入口。 */
fun aiPhotoEntryAvailable(onClick: (() -> Unit)?): Boolean = onClick != null

/** 点拍照入口；未实现返回 false，已实现时恰好回调一次。 */
fun triggerAiPhotoEntry(onClick: (() -> Unit)?): Boolean {
    if (onClick == null) return false
    onClick()
    return true
}

// ---- 三、解析入库（合法结构化数据替换状态，非法数据原样拒绝）----

/** AI 页面持有的卡片状态。 */
data class AiCardsState(
    val diet: DietCard? = null,
    val exercise: ExerciseCard? = null,
    val metrics: List<MetricCard> = emptyList(),
)

/** 待入库的解析结果（模型已结构化，但尚未校验）。 */
sealed interface AiCardPayload {
    data class Diet(val card: DietCard) : AiCardPayload
    data class Exercise(val card: ExerciseCard) : AiCardPayload
    data class Metric(val card: MetricCard) : AiCardPayload
}

/** 入库结果：只有 [Admitted] 携带新状态；[Rejected] 时调用方必须保留原状态。 */
sealed interface AiCardAdmission {
    data class Admitted(val state: AiCardsState) : AiCardAdmission
    data class Rejected(val reason: String) : AiCardAdmission
}

/** 饮食卡校验：缺日期 / 缺餐次 / 餐次字段为空或为负都拒绝。 */
fun dietCardRejectReason(card: DietCard): String? {
    if (card.date.isBlank()) return "饮食卡缺少日期"
    if (card.meals.isEmpty()) return "饮食卡缺少餐次"
    card.meals.forEach { meal ->
        if (meal.name.isBlank()) return "饮食卡存在未命名餐次"
        if (meal.calories < 0) return "饮食卡热量不能为负"
        if (meal.protein < 0 || meal.carbs < 0 || meal.fat < 0) return "饮食卡营养素不能为负"
    }
    return null
}

/** 运动卡校验：缺名称 / 时长或消耗非正数都拒绝。 */
fun exerciseCardRejectReason(card: ExerciseCard): String? {
    if (card.name.isBlank()) return "运动卡缺少名称"
    if (card.duration <= 0) return "运动卡时长必须为正"
    if (card.calories < 0) return "运动卡消耗不能为负"
    if (card.heartRate != null && card.heartRate <= 0) return "运动卡心率必须为正"
    return null
}

/** 指标卡校验：缺指标名 / 缺数值 / 等级越界都拒绝。 */
fun metricCardRejectReason(card: MetricCard): String? {
    if (card.metricName.isBlank()) return "指标卡缺少指标名"
    if (card.value.isBlank()) return "指标卡缺少数值"
    if (card.level.isBlank()) return "指标卡缺少等级"
    if (card.tone !in 0..2) return "指标卡等级越界"
    return null
}

/** 纯函数入库：返回新状态或拒绝原因，绝不修改传入的 [state]。 */
fun admitAiCard(state: AiCardsState, payload: AiCardPayload): AiCardAdmission = when (payload) {
    is AiCardPayload.Diet -> {
        val reason = dietCardRejectReason(payload.card)
        if (reason != null) {
            AiCardAdmission.Rejected(reason)
        } else {
            AiCardAdmission.Admitted(state.copy(diet = payload.card))
        }
    }

    is AiCardPayload.Exercise -> {
        val reason = exerciseCardRejectReason(payload.card)
        if (reason != null) {
            AiCardAdmission.Rejected(reason)
        } else {
            AiCardAdmission.Admitted(state.copy(exercise = payload.card))
        }
    }

    is AiCardPayload.Metric -> {
        val reason = metricCardRejectReason(payload.card)
        if (reason != null) {
            AiCardAdmission.Rejected(reason)
        } else {
            AiCardAdmission.Admitted(state.copy(metrics = state.metrics + payload.card))
        }
    }
}

/**
 * 极简入库器：视图状态持有者调用 [submit] 即可，只有通过校验才推进 [state]。
 *
 * 之所以另起一层而不是直接暴露可变字段：验收要求「非法数据被拒绝且不改状态」，
 * 状态只在这里被赋值，单测就能证明拒绝路径没有任何副作用。
 */
class AiCardsStore(initial: AiCardsState = AiCardsState()) {

    var state: AiCardsState = initial
        private set

    /** 入库；返回是否成功，被拒绝时 [state] 保持原样。 */
    fun submit(payload: AiCardPayload): Boolean = when (val result = admitAiCard(state, payload)) {
        is AiCardAdmission.Admitted -> {
            state = result.state
            true
        }

        is AiCardAdmission.Rejected -> false
    }
}

// ---- 四、Compose 呈现 ----

/**
 * ai-diet-card — 结构化饮食卡（DEC-043-B）。
 * 日期切换 + 餐次列表 + 编辑/删除/保存按钮。
 */
@Composable
fun AiDietCard(
    card: DietCard,
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${AiCardText.DietTitlePrefix} ${card.date}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        card.meals.forEach { meal ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(meal.name, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
                    Text(
                        dietMacroText(meal),
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                    )
                }
                Text(
                    dietCaloriesText(meal),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            QkButton(
                text = AiCardText.MealEdit,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onEdit,
            )
            Spacer(modifier = Modifier.width(8.dp))
            QkButton(
                text = AiCardText.MealDelete,
                style = QkButtonStyle.Ghost,
                modifier = Modifier.weight(1f),
                onClick = onDelete,
            )
            Spacer(modifier = Modifier.width(8.dp))
            QkButton(
                text = AiCardText.MealSave,
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }
    }
}

/**
 * ai-exercise-card — 运动卡（DEC-043-B）。
 */
@Composable
fun AiExerciseCard(card: ExerciseCard) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Text(
                    exerciseSummaryText(card),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            exerciseHeartRateText(card)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Accent,
                )
            }
        }
    }
}

/**
 * ai-metric-card — 指标解读卡（DEC-043-B，参考 AiChat.kt:161-180）。
 */
@Composable
fun AiMetricCard(card: MetricCard) {
    val toneColor = when (card.tone) {
        0 -> QkColor.Muted
        1 -> QkColor.Primary
        else -> QkColor.Accent
    }
    QkCard {
        Text(card.metricName, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = toneColor,
            )
            Text(card.level, style = MaterialTheme.typography.bodySmall, color = toneColor)
        }
        Text(
            card.advice,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/**
 * ai-welcome — AI 欢迎块 + 示例问题（DEC-043-B，参考 AiChat.kt:341-362）。
 *
 * [onExampleClick] 为空表示示例引导尚未接入：示例只作说明文字，不加点击态，
 * 避免渲染成点不动的死入口。
 */
@Composable
fun AiWelcome(
    onExampleClick: ((String) -> Unit)? = null,
) {
    QkCard {
        Text(AiCardText.WelcomeTitle, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Text(
            AiCardText.WelcomeIntro,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(AiCardText.WelcomeExamplesHint, style = MaterialTheme.typography.labelSmall, color = QkColor.Hint)
        AiCardText.WelcomeExamples.forEach { q ->
            val pill = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(QkColor.Mint)
            Text(
                AiCardText.WelcomeExampleBullet + q,
                modifier = if (aiExampleClickAvailable(onExampleClick)) {
                    pill.clickable { triggerAiExample(onExampleClick, q) }
                } else {
                    pill
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Primary,
            )
        }
    }
}

/**
 * ai-photo-entry — 拍照记录入口（DEC-043-B）。
 *
 * 相机 / 选图未接入时 [onClick] 传 null：此时整个入口不渲染，
 * 不会出现「看起来能点、点了没反应」的按钮。
 */
@Composable
fun AiPhotoEntry(onClick: (() -> Unit)? = null) {
    if (aiPhotoEntryAvailable(onClick)) {
        QkButton(
            text = AiCardText.PhotoEntry,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { triggerAiPhotoEntry(onClick) },
        )
    }
}
