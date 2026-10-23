package com.xcwk.scalehub.feature.privacy

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

@Composable
internal fun PrivacyAuthPage(
    state: PrivacyFlowState,
    onEvent: (PrivacyEvent) -> Unit,
) {
    var body by remember(state.shareBodyComposition) { mutableStateOf(state.shareBodyComposition) }
    var profile by remember(state.shareHealthProfile) { mutableStateOf(state.shareHealthProfile) }
    var cloud by remember(state.cloudSync) { mutableStateOf(state.cloudSync) }
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
                text = PrivacyCopy.AUTH_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        QkIconBubble(icon = QkIcons.About, tone = QkGlyphTone.Green)
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(text = PrivacyCopy.AUTH_HEAD, color = QkColor.Fg, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(QkSpacing.Sm))
        Text(text = PrivacyCopy.AUTH_SUB, color = QkColor.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard {
            ConsentCheck(PrivacyCopy.BODY_COMPOSITION, PrivacyCopy.BODY_COMPOSITION_DETAIL, body) { body = it }
            ConsentCheck(PrivacyCopy.HEALTH_PROFILE, PrivacyCopy.HEALTH_PROFILE_DETAIL, profile) { profile = it }
            ConsentCheck(PrivacyCopy.CLOUD_SYNC, PrivacyCopy.CLOUD_SYNC_DETAIL, cloud) { cloud = it }
        }
        Spacer(Modifier.height(QkSpacing.Md))
        Text(text = PrivacyCopy.AUTH_NOTE, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(QkSpacing.Lg))
        QkButton(
            text = PrivacyCopy.AGREE,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onEvent(PrivacyEvent.AgreeAndContinue(body, profile, cloud))
            },
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = PrivacyCopy.LOCAL_ONLY,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEvent(PrivacyEvent.LocalOnly) },
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
private fun ConsentCheck(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(vertical = QkSpacing.Sm)) {
            Text(text = title, color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
            Text(text = detail, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(
                checkedColor = QkColor.Primary,
                uncheckedColor = QkColor.Muted,
            ),
        )
    }
}

@Composable
internal fun PrivacyGrantedPage(
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
                text = PrivacyCopy.GRANTED_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(icon = QkIcons.Privacy, tone = QkGlyphTone.Green)
                Spacer(Modifier.width(QkSpacing.Md))
                Column {
                    Text(
                        text = PrivacyCopy.GRANTED_STATUS,
                        color = QkColor.Fg,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.grantedOnLabel.isNotEmpty()) {
                        Text(
                            text = state.grantedOnLabel,
                            color = QkColor.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        QkListRow(
            title = PrivacyCopy.RULES_ENTRY,
            subtitle = PrivacyCopy.RULES_ENTRY_DETAIL,
            onClick = { onEvent(PrivacyEvent.OpenRules) },
        )
        QkListRow(
            title = PrivacyCopy.PREVIEW_ENTRY,
            subtitle = PrivacyCopy.PREVIEW_ENTRY_DETAIL,
            onClick = { onEvent(PrivacyEvent.PreviewWithdraw) },
        )
        QkListRow(
            title = PrivacyCopy.WITHDRAW_ALL,
            subtitle = PrivacyCopy.WITHDRAW_ALL_DETAIL,
            onClick = { onEvent(PrivacyEvent.OpenWithdrawSheet) },
        )
        Spacer(Modifier.height(QkSpacing.Md))
        Text(text = PrivacyCopy.GRANTED_NOTE, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}
