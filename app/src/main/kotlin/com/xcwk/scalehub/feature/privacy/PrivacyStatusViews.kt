package com.xcwk.scalehub.feature.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * v-unauth 撤回后状态 + v-consult 未授权咨询 + v-rules 删除保留规则（T-096 拆分的子文件之二）。
 *
 * 原型 `10-privacy-authorization.html`：
 * - `v-unauth`：个性化分析已关闭 + 撤回时间 + 重新开启 | 受影响的功能四项 | 去一般咨询
 * - `v-consult`：未开启个性化 banner | 引导问题气泡 | 通用知识 intro | 输入框
 * - `v-rules`：四种操作范围不同 banner | 四类删除操作行 | 保留规则三条
 */

/** 撤回受影响行的图标色调：受影响=警示（橙），不受影响=正常（绿）。 */
private fun affectedTone(affected: Boolean): QkGlyphTone =
    if (affected) QkGlyphTone.Orange else QkGlyphTone.Green

/** v-unauth 撤回后状态页。 */
@Composable
fun PrivacyUnauthView(
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
        PrivacyNavBar(TITLE_UNAUTH, onBack)

        // 撤回状态卡：锁图标弱化 + 已关闭 + 撤回时间 + 重新开启（原型 .card.soft）
        PrivacyStatusCard(
            icon = QkIcons.Privacy,
            iconTint = QkColor.Muted,
            title = UNAUTH_OFF,
            subtitle = UNAUTH_WITHDRAWN_AT_PREFIX + state.withdrawnAt,
            modifier = Modifier.padding(top = QkSpacing.Md),
            trailing = {
                // 原型 span[data-goto=v-auth]：重新开启（回到授权页重新单独同意）
                Text(
                    UNAUTH_RE_ENABLE,
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                    modifier = Modifier
                        .padding(start = QkSpacing.Sm)
                        .clickable { onIntent(PrivacyAuthIntent.ReEnable) },
                )
            },
        )

        Text(
            UNAUTH_AFFECTED_LABEL,
            modifier = Modifier.padding(top = QkSpacing.Lg, start = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.GhostInk,
        )

        // 受影响功能清单（原型 .rows 四 .row：前三 warn，测量与记录 soft ✓）
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xs)) {
            AFFECTED_FUNCTIONS.forEachIndexed { index, item ->
                if (index > 0) QkDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
                    verticalAlignment = Alignment.Top,
                ) {
                    QkIconBubble(
                        icon = if (item.affected) QkIcons.About else QkIcons.Habit,
                        tone = affectedTone(item.affected),
                        size = 32.dp,
                    )
                    Column(modifier = Modifier.padding(start = QkSpacing.Md)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                        Text(
                            item.desc,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.GhostInk,
                        )
                    }
                }
            }
        }

        Text(
            UNAUTH_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = UNAUTH_GO_CONSULT,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Primary,
            onClick = { onIntent(PrivacyAuthIntent.GoConsult) },
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

/** v-consult 未授权一般咨询页（呈现式：提问经 onAskSuggestion 交由既有 AI 流程）。 */
@Composable
fun PrivacyConsultView(
    state: PrivacyAuthUiState,
    onIntent: (PrivacyAuthIntent) -> Unit,
    onBack: () -> Unit,
    onAskSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        PrivacyNavBar(TITLE_CONSULT, onBack) {
            // 原型 .navbar .lnk[data-goto=v-auth]：开启个性化（引导开启入口）
            Text(
                CONSULT_ENABLE_LINK,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Primary,
                modifier = Modifier.clickable { onIntent(PrivacyAuthIntent.OpenPersonalization) },
            )
        }

        // 未开启个性化 banner（原型 .banner：仅提供通用健康知识）
        PrivacyBanner(
            icon = QkIcons.Privacy,
            text = CONSULT_BANNER,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )

        // 引导问题气泡（原型两个 pill → data-toast 通用回答）
        Row(modifier = Modifier.padding(top = QkSpacing.Md)) {
            CONSULT_SUGGESTIONS.forEach { question ->
                QkChip(
                    label = question,
                    selected = false,
                    onClick = { onAskSuggestion(question) },
                )
            }
        }

        // 助手 intro 气泡（原型不对称圆角 .card，此处以 QkCard 呈现同文案）
        QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
            Text(
                CONSULT_INTRO,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.GhostInk,
            )
        }

        // 输入占位（原型 .field .fake.ph「问问健康知识…」；真实输入框由既有 AI 页承担）
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        Text(
            text = state.consultInput.ifEmpty { CONSULT_INPUT_HINT },
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.consultInput.isEmpty()) QkColor.Hint else QkColor.Fg,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

/** v-rules 数据删除与保留规则页。 */
@Composable
fun PrivacyRulesView(
    onBack: () -> Unit,
    onDeleteOperation: (DeleteOp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        PrivacyNavBar(TITLE_RULES, onBack)

        // 四类操作提示 banner（原型 .banner：按需选择避免误删）
        PrivacyBanner(
            icon = QkIcons.About,
            text = RULES_BANNER,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )

        // 四类删除操作行（原型 .rows 四 .row：前三 soft 图标，注销账号 danger）
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            DELETE_OPERATIONS.forEachIndexed { index, op ->
                if (index > 0) QkDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeleteOperation(op.op) }
                        .padding(vertical = QkSpacing.Md),
                    verticalAlignment = Alignment.Top,
                ) {
                    QkIconBubble(
                        icon = iconFor(op.op),
                        tone = if (op.danger) QkGlyphTone.Orange else QkGlyphTone.Green,
                        size = 38.dp,
                    )
                    Column(modifier = Modifier.padding(start = QkSpacing.Md)) {
                        Text(
                            op.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (op.danger) QkColor.Danger else QkColor.Fg,
                        )
                        Text(
                            op.desc,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.GhostInk,
                        )
                    }
                }
            }
        }

        // 保留规则卡（原型 .card.lg 三条 hint）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            Text(RULES_RETENTION_TITLE, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            RETENTION_RULES.forEach { rule ->
                Text(
                    "· $rule",
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

/** 四类删除操作的图标选型（记录→历史、对话→AI、清空→清理、注销→永久删除）。 */
private fun iconFor(op: DeleteOp): ImageVector = when (op) {
    DeleteOp.SingleRecord -> QkIcons.History
    DeleteOp.SingleConversation -> QkIcons.Ai
    DeleteOp.AllConversations -> QkIcons.Cache
    DeleteOp.Account -> QkIcons.DeleteAccount
}
