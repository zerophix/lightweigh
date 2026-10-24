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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * v-auth 授权页 + v-granted 已授权管理页（T-096 文件拆分的子文件之一）。
 *
 * 原型 `10-privacy-authorization.html`：
 * - `v-auth`：健康数据属于敏感信息 | 身体成分数据 / 健康档案信息（必选）+ 云端同步（可选）
 *   | 同意并继续 / 仅本机，暂不同步
 * - `v-granted`：健康数据授权已开启 + 开启时间 | 数据删除与保留规则 / 撤回授权后会怎样
 *   | 撤回全部授权（危险色）| 撤回确认弹层
 *
 * 授权写入不在本组件发生：同意动作经 [PrivacyAuthFlow] 的 onConsentGranted 回调上交。
 */

/** v-auth 授权页。 */
@Composable
fun PrivacyAuthView(
    state: PrivacyAuthUiState,
    onIntent: (PrivacyAuthIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        PrivacyNavBar(TITLE_AUTH, onBack)

        // hero：盾形图标 + 敏感信息声明（原型 .bigicon.ring + .h1.sm/.h2）
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkIcon(QkIcons.Privacy, tint = QkColor.Primary, size = 76.dp)
            Text(
                AUTH_HEADLINE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            Text(
                AUTH_SUBLINE,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.GhostInk,
            )
        }

        // 数据项卡：两类必选 + 一行可选云同步（原型 .card.lg 三 .row）
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl), padding = QkSpacing.Md) {
            AUTH_ITEMS.forEachIndexed { index, item ->
                if (index > 0) QkDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (item.required) {
                                Modifier
                            } else {
                                Modifier.clickable { onIntent(PrivacyAuthIntent.ToggleCloudSync) }
                            },
                        )
                        .padding(vertical = QkSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                        Text(
                            item.desc,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.GhostInk,
                        )
                    }
                    PrivacyCheck(checked = if (item.required) true else state.cloudSyncSelected)
                }
            }
        }

        Text(
            AUTH_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = AUTH_AGREE,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = { onIntent(PrivacyAuthIntent.AgreeAndContinue) },
            )
            QkButton(
                text = AUTH_LOCAL_ONLY,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = { onIntent(PrivacyAuthIntent.LocalOnly) },
            )
        }
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

/** v-granted 已授权管理页（含撤回确认弹层）。 */
@Composable
fun PrivacyGrantedView(
    state: PrivacyAuthUiState,
    onIntent: (PrivacyAuthIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        PrivacyNavBar(TITLE_GRANTED, onBack)

        // 授权状态卡：锁图标 + 已开启 + 开启时间（原型 .card.soft）
        PrivacyStatusCard(
            icon = QkIcons.Privacy,
            iconTint = QkColor.Primary,
            title = GRANTED_ON,
            subtitle = state.grantedAt,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )

        // 两个入口行（原型 .rows 两 .row → data-goto v-rules / v-unauth）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            QkListRow(
                title = ENTRY_RULES,
                subtitle = ENTRY_RULES_DESC,
                onClick = { onIntent(PrivacyAuthIntent.OpenRules) },
                trailing = { QkIcon(QkIcons.ChevronRight, size = QkSpacing.Lg) },
            )
            QkListRow(
                title = ENTRY_WITHDRAW_PREVIEW,
                subtitle = ENTRY_WITHDRAW_PREVIEW_DESC,
                onClick = { onIntent(PrivacyAuthIntent.PreviewWithdraw) },
                trailing = { QkIcon(QkIcons.ChevronRight, size = QkSpacing.Lg) },
                showDivider = false,
            )
        }

        // 撤回全部授权（危险行，原型单独 .rows）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            QkListRow(
                title = REVOKE_ALL,
                subtitle = REVOKE_ALL_DESC,
                onClick = { onIntent(PrivacyAuthIntent.RequestWithdraw) },
                showDivider = false,
            )
        }

        Text(
            GRANTED_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }

    // 撤回确认弹层（原型 overlay#sheet-withdraw：确认撤回危险色 + 保持开启）。
    // QkButton 无危险态，按仓库既有 AlertDialog 口径用 TextButton，危险色经 QkColor.Danger 取 token。
    if (state.withdrawSheetVisible) {
        AlertDialog(
            onDismissRequest = { onIntent(PrivacyAuthIntent.KeepGranted) },
            title = { Text(WITHDRAW_SHEET_TITLE) },
            text = { Text(WITHDRAW_SHEET_BODY) },
            confirmButton = {
                TextButton(onClick = { onIntent(PrivacyAuthIntent.ConfirmWithdraw) }) {
                    Text(WITHDRAW_CONFIRM, color = QkColor.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(PrivacyAuthIntent.KeepGranted) }) {
                    Text(WITHDRAW_KEEP, color = QkColor.Primary)
                }
            },
        )
    }
}
