package com.xcwk.scalehub.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 记录编辑中可改动的字段（T-081 · Wave 1）。
 *
 * 原型 `v-edit`：「测量数值不可修改」「仅时间与备注可编辑，数值来自设备保证真实。」
 */
enum class RecordEditField { Time, Note, Weight, FatRate, Muscle, Bmi }

/**
 * 字段是否允许编辑 —— **原型硬约束的可判定形式**。
 *
 * 仅 [RecordEditField.Time] 与 [RecordEditField.Note] 可改，
 * 其余测量字段来自体脂秤读数，改动即破坏数据真实性。
 * 抽成纯函数是为了能在 JVM 单测里钉死，而不是靠"编辑页做得像"来保证。
 */
fun isEditable(field: RecordEditField): Boolean = when (field) {
    RecordEditField.Time,
    RecordEditField.Note,
    -> true

    RecordEditField.Weight,
    RecordEditField.FatRate,
    RecordEditField.Muscle,
    RecordEditField.Bmi,
    -> false
}

/**
 * 一次编辑的完整状态。
 *
 * @property recordId 必须与被编辑记录一致；不一致意味着"编辑写到了另一条记录"，
 * 与 AC-HT3（详情与报告同源 recordId）属同一类"数据必须同源"约束
 */
data class RecordEditModel(
    val recordId: Long,
    val timeText: String,
    val note: String,
    val weightText: String,
    val fatRateText: String,
    val muscleText: String,
    val bmiText: String,
)

/**
 * 校验一次编辑是否合法，返回违规项（空列表 = 可保存）。
 *
 * ## 判定依据（原型 `v-edit` + AC-HT3）
 * 1. 测量字段（体重/体脂率/肌肉量/BMI）被改动 → 违规：数值来自设备，不可修改
 * 2. [RecordEditModel.recordId] 与原始记录不同 → 违规：编辑必须落在同一记录上（同源约束）
 *
 * 时间与备注的改动**不产生**违规 —— 它们正是允许编辑的两项。
 */
fun validateRecordEdit(original: RecordEditModel, edited: RecordEditModel): List<String> {
    val violations = mutableListOf<String>()
    if (original.recordId != edited.recordId) {
        violations += "编辑记录与原始记录不一致（recordId 不同源）"
    }
    val lockedFields = mapOf(
        "测量数值" to (original.weightText to edited.weightText),
        "体脂率" to (original.fatRateText to edited.fatRateText),
        "肌肉量" to (original.muscleText to edited.muscleText),
        "BMI" to (original.bmiText to edited.bmiText),
    )
    for ((name, pair) in lockedFields) {
        if (pair.first != pair.second) {
            violations += "$name 来自设备，不可修改"
        }
    }
    return violations
}

/**
 * 记录编辑页（T-081 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/05-history-trend.html` → `v-edit`
 * > 编辑记录 | 测量数值不可修改 | 62.4 kg | 测量时间 | 备注（可选）
 * > 晨起、空腹、排便后测量 | 仅时间与备注可编辑，数值来自设备保证真实。| 保存修改
 *
 * ## 验收
 * - 测量字段以**只读文案**呈现，编辑页根本拿不到改动入口
 * - 保存前调用方应执行 [validateRecordEdit]；违规项为非空时不得落库
 * - 单测见 `RecordEditValidationTest`（字段可编辑性 + 同源 + 测量值锁定）
 *
 * ## 用法
 * 与 [HistoryDetailScreen] 同为呈现式组件：状态由既有 `HistoryViewModel` 供给。
 * 时间选择器不内置（仓库已有 `QkWheelPicker`），通过 [onPickTime] 交给调用方。
 */
@Composable
fun RecordEditScreen(
    model: RecordEditModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onPickTime: () -> Unit = {},
    onSave: () -> Unit = {},
    saveViolations: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = "编辑记录",
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkSectionHead("测量数值不可修改")

        Spacer(Modifier.height(QkSpacing.CardGap))
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                ReadOnlyRow(label = "体重", value = model.weightText)
                ReadOnlyRow(label = "体脂率", value = model.fatRateText)
                ReadOnlyRow(label = "肌肉量", value = model.muscleText)
                ReadOnlyRow(label = "BMI", value = model.bmiText)
                Spacer(Modifier.height(QkSpacing.Sm))
                Text(
                    text = "仅时间与备注可编辑，数值来自设备保证真实。",
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkSectionHead("测量时间")
        Spacer(Modifier.height(QkSpacing.CardGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickTime),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.timeText,
                color = QkColor.Fg,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "修改",
                color = QkColor.Primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkTextField(
            value = model.note,
            onValueChange = onNoteChange,
            label = "备注（可选）",
            placeholder = "晨起、空腹、排便后测量",
            modifier = Modifier.fillMaxWidth(),
        )

        if (saveViolations.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Md))
            saveViolations.forEach { violation ->
                Text(
                    text = violation,
                    color = QkColor.Danger,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = "保存修改",
            modifier = Modifier.fillMaxWidth(),
            enabled = saveViolations.isEmpty(),
            onClick = onSave,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 只读字段行：左侧标签、右侧值，无任何点击入口。 */
@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = QkSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = QkColor.Fg,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
