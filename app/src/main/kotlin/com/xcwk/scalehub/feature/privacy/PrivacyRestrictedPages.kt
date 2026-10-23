package com.xcwk.scalehub.feature.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.xcwk.scalehub.ui.components.QkActionChip
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

@Composable
internal fun PrivacyUnauthPage(
    state: PrivacyFlowState,
    onEvent: (PrivacyEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = { onEvent(PrivacyEvent.Back) })
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = PrivacyCopy.UNAUTH_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(icon = QkIcons.Privacy, tone = QkGlyphTone.Gray)
                Spacer(Modifier.width(QkSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PrivacyCopy.UNAUTH_STATUS,
                        color = QkColor.Fg,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.withdrawnOnLabel.isNotEmpty()) {
                        Text(
                            text = "${PrivacyCopy.WITHDRAWN_ON}：${state.withdrawnOnLabel}",
                            color = QkColor.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    text = PrivacyCopy.REENABLE,
                    color = QkColor.Primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onEvent(PrivacyEvent.Reenable) },
                )
            }
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            text = PrivacyCopy.AFFECTED,
            color = QkColor.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
        QkListRow(title = PrivacyCopy.REPORT, subtitle = PrivacyCopy.REPORT_DETAIL, tone = QkGlyphTone.Orange)
        QkListRow(title = PrivacyCopy.TREND, subtitle = PrivacyCopy.TREND_DETAIL, tone = QkGlyphTone.Orange)
        QkListRow(title = PrivacyCopy.WEEKLY, subtitle = PrivacyCopy.WEEKLY_DETAIL, tone = QkGlyphTone.Orange)
        QkListRow(title = PrivacyCopy.MEASURE, subtitle = PrivacyCopy.MEASURE_DETAIL, tone = QkGlyphTone.Green)
        Spacer(Modifier.height(QkSpacing.Md))
        Text(text = PrivacyCopy.UNAUTH_NOTE, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(QkSpacing.Lg))
        QkButton(
            text = PrivacyCopy.GO_CONSULT,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEvent(PrivacyEvent.OpenConsult) },
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
internal fun PrivacyConsultPage(
    restricted: Boolean,
    onEvent: (PrivacyEvent) -> Unit,
    onAsk: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = { onEvent(PrivacyEvent.Back) })
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = PrivacyCopy.CONSULT_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = PrivacyCopy.OPEN_PERSONAL,
                color = QkColor.Primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onEvent(PrivacyEvent.OpenPersonalization) },
            )
        }
        if (restricted) {
            Spacer(Modifier.height(QkSpacing.Lg))
            QkCard {
                Text(
                    text = PrivacyCopy.CONSULT_BANNER,
                    color = QkColor.Fg,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(QkSpacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkActionChip(text = PrivacyCopy.CHIP_BMI, onClick = { onAsk(PrivacyCopy.CHIP_BMI) })
            QkActionChip(text = PrivacyCopy.CHIP_FAT, onClick = { onAsk(PrivacyCopy.CHIP_FAT) })
        }
        Spacer(Modifier.height(QkSpacing.Md))
        QkCard {
            Text(
                text = PrivacyCopy.CONSULT_CARD,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = PrivacyCopy.CONSULT_FIELD,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(QkSpacing.Sm))
            QkIcon(
                icon = QkIcons.Send,
                tint = QkColor.Primary,
                modifier = Modifier.clickable {
                    if (draft.isNotBlank()) onAsk(draft)
                },
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
internal fun PrivacyRulesPage(
    onEvent: (PrivacyEvent) -> Unit,
    onRuleSelected: (DeletionRule) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = { onEvent(PrivacyEvent.Back) })
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = PrivacyCopy.RULES_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard {
            Text(
                text = PrivacyCopy.RULES_BANNER,
                color = QkColor.Fg,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        deletionRules().forEach { rule ->
            QkListRow(
                title = rule.title,
                subtitle = rule.effect,
                tone = if (rule.destructive) QkGlyphTone.Orange else QkGlyphTone.Green,
                onClick = { onRuleSelected(rule) },
            )
        }
        Spacer(Modifier.height(QkSpacing.Md))
        QkCard {
            Text(
                text = PrivacyCopy.RETENTION_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = PrivacyCopy.RETENTION,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}
