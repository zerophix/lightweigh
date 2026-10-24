@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.components.QkVSpace
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * mine — 账号注销四态流程（原型07 v-closure-apply / verify / processing / result）。
 *
 * 文件分工照 PasswordReset 先例：静态文案（逐字取自原型）+ 纯函数（冷静期
 * 与验证码倒计时，JVM 单测钉死）+ 四个无状态 Screen。倒计时秒数由调用方
 * 注入，组件不自带计时器（呈现式，DEC-061 口径）。
 *
 * 门禁规则（AC 末条）：冷静期剩余 > 0 时必须展示撤销入口；归零后才允许
 * 进入结果页——由 [canEnterResult] 纯函数钉死。
 */

// ---- 静态文案（逐字取自原型 07 v-closure-*） ----

const val TITLE_CLOSURE_APPLY = "注销账号"
const val HEAD_CLOSURE_BEFORE = "注销前，请了解"
const val WARN_CLOSURE_IRREVERSIBLE = "注销是不可逆操作。"
const val CONSEQUENCE_DELETE_RECORDS = "全部测量记录将删除"
const val CONSEQUENCE_NO_RECOVERY = "本地与云端均不可恢复"
const val CONSEQUENCE_UNLINK = "AI 对话与家庭成员关联将解除"
const val ACTION_CLOSURE_NEXT = "我已了解，下一步"
const val ACTION_CLOSURE_RECONSIDER = "再想想"

const val TITLE_CLOSURE_VERIFY = "身份验证"
const val HEAD_CLOSURE_VERIFY = "验证是你本人"
const val LABEL_CLOSURE_SEND_TO = "验证码将发送至"
const val CLOSURE_PHONE_MASKED = "138****0000"
const val LABEL_CLOSURE_CODE = "验证码"
const val RESEND_COUNTDOWN_TEMPLATE_CLOSURE = "%ds 后重发"
const val HINT_CLOSURE_COOLING = "验证后进入 7 天冷静期，期间登录可撤销。"
const val ACTION_CONFIRM_CLOSURE = "确认注销"

const val TITLE_CLOSURE_SUBMITTED = "注销申请已提交"
const val TITLE_CLOSURE_PROCESSING = "注销申请处理中"
const val LABEL_CLOSURE_ENTER = "账号进入"
const val LABEL_CLOSURE_COOLING_DAYS = "7 天冷静期"
const val LABEL_CLOSURE_COOLING_TAIL = "，期间登录可撤销。"
const val LABEL_CLOSURE_REMAINING = "剩余冷静期"
const val HINT_CLOSURE_PROTECTED = "冷静期内数据仍受保护，不会被使用。"
const val ACTION_CLOSURE_REVOKE = "撤销注销，恢复账号"
const val ACTION_CLOSURE_SIMULATE_END = "模拟冷静期结束 → 结果"

const val TITLE_CLOSURE_RESULT = "账号已注销"
const val HEAD_CLOSURE_RESULT_DELETED = "你的账号与相关数据已按规则删除。"
const val HEAD_CLOSURE_RESULT_THANKS = "感谢你曾使用轻衡。"
const val ACTION_CLOSURE_BACK = "返回（演示结束）"

/** 注销后果三项（v-closure-apply 列表）。 */
val CLOSURE_CONSEQUENCES = listOf(
    CONSEQUENCE_DELETE_RECORDS,
    CONSEQUENCE_NO_RECOVERY,
    CONSEQUENCE_UNLINK,
)

// ---- 纯函数（JVM 单测钉死） ----

/** 冷静期剩余时长格式化：`6天 14时`（v-closure-processing 原文形态）。 */
fun coolingRemainingLabel(totalHours: Long): String {
    val days = totalHours / 24
    val hours = totalHours % 24
    return "${days}天 ${hours}时"
}

/** 验证码重发倒计时文案：剩余秒 > 0 时「58s 后重发」，归零后「重新发送」。 */
fun closureResendLabel(secondsLeft: Int): String =
    if (secondsLeft > 0) RESEND_COUNTDOWN_TEMPLATE_CLOSURE.format(secondsLeft)
    else ACTION_RESEND_FALLBACK

const val ACTION_RESEND_FALLBACK = "重新发送"

/** 剩余冷静期 > 0：必须展示撤销入口，且不得进入结果页。 */
fun canRevokeClosure(coolingHoursLeft: Long): Boolean = coolingHoursLeft > 0

/** 冷静期归零才允许进入结果页（AC 末条门禁）。 */
fun canEnterResult(coolingHoursLeft: Long): Boolean = coolingHoursLeft <= 0

// ---- 四态无状态 Screen ----

/** v-closure-apply：注销须知与后果三项。 */
@Composable
fun ClosureApplyScreen(
    onNext: () -> Unit,
    onReconsider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QkCard {
            Column {
                Text(TITLE_CLOSURE_APPLY, style = MaterialTheme.typography.titleLarge)
                QkVSpace(QkSpacing.Sm)
                Text(
                    HEAD_CLOSURE_BEFORE,
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Muted,
                )
                QkVSpace(QkSpacing.Sm)
                Text(
                    WARN_CLOSURE_IRREVERSIBLE,
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Danger,
                )
                QkVSpace(QkSpacing.Sm)
                CLOSURE_CONSEQUENCES.forEach { consequence ->
                    Text(
                        "· $consequence",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Xs),
                    )
                }
            }
        }
        QkVSpace(QkSpacing.Md)
        QkButton(text = ACTION_CLOSURE_NEXT, onClick = onNext, modifier = Modifier.fillMaxWidth())
        QkVSpace(QkSpacing.Sm)
        TextButton(onClick = onReconsider, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(ACTION_CLOSURE_RECONSIDER, color = QkColor.Muted)
        }
    }
}

/** v-closure-verify：身份验证（脱敏手机号 + 验证码 + 重发倒计时）。 */
@Composable
fun ClosureVerifyScreen(
    resendSecondsLeft: Int,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QkCard {
            Column {
                Text(TITLE_CLOSURE_VERIFY, style = MaterialTheme.typography.titleLarge)
                QkVSpace(QkSpacing.Sm)
                Text(HEAD_CLOSURE_VERIFY, style = MaterialTheme.typography.titleMedium, color = QkColor.Muted)
                QkVSpace(QkSpacing.Sm)
                Text(LABEL_CLOSURE_SEND_TO, style = MaterialTheme.typography.bodyMedium, color = QkColor.Muted)
                Text(CLOSURE_PHONE_MASKED, style = MaterialTheme.typography.bodyLarge)
                QkVSpace(QkSpacing.Sm)
                QkTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = LABEL_CLOSURE_CODE,
                    modifier = Modifier.fillMaxWidth(),
                )
                QkVSpace(QkSpacing.Xs)
                Text(
                    closureResendLabel(resendSecondsLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
                QkVSpace(QkSpacing.Sm)
                Text(HINT_CLOSURE_COOLING, style = MaterialTheme.typography.bodySmall, color = QkColor.Sub)
            }
        }
        QkVSpace(QkSpacing.Md)
        QkButton(
            text = ACTION_CONFIRM_CLOSURE,
            onClick = onConfirm,
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** v-closure-processing：冷静期展示（倒计时 + 撤销入口，归零后可进结果页）。 */
@Composable
fun ClosureProcessingScreen(
    coolingHoursLeft: Long,
    onRevoke: () -> Unit,
    onSimulateEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QkCard {
            Column {
                Text(TITLE_CLOSURE_SUBMITTED, style = MaterialTheme.typography.titleLarge)
                QkVSpace(QkSpacing.Xs)
                Text(TITLE_CLOSURE_PROCESSING, style = MaterialTheme.typography.bodyMedium, color = QkColor.Muted)
                QkVSpace(QkSpacing.Sm)
                Text(
                    "$LABEL_CLOSURE_ENTER $LABEL_CLOSURE_COOLING_DAYS$LABEL_CLOSURE_COOLING_TAIL",
                    style = MaterialTheme.typography.bodyMedium,
                )
                QkVSpace(QkSpacing.Sm)
                Text(LABEL_CLOSURE_REMAINING, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                Text(coolingRemainingLabel(coolingHoursLeft), style = MaterialTheme.typography.headlineSmall)
                QkVSpace(QkSpacing.Sm)
                Text(HINT_CLOSURE_PROTECTED, style = MaterialTheme.typography.bodySmall, color = QkColor.Sub)
            }
        }
        QkVSpace(QkSpacing.Md)
        if (canRevokeClosure(coolingHoursLeft)) {
            QkButton(
                text = ACTION_CLOSURE_REVOKE,
                onClick = onRevoke,
                modifier = Modifier.fillMaxWidth(),
            )
            QkVSpace(QkSpacing.Sm)
        }
        TextButton(onClick = onSimulateEnd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(ACTION_CLOSURE_SIMULATE_END, color = QkColor.Muted)
        }
    }
}

/** v-closure-result：注销完成。 */
@Composable
fun ClosureResultScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(QkSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QkPill(text = TITLE_CLOSURE_RESULT, tone = QkPillTone.Gray)
        QkVSpace(QkSpacing.Md)
        Text(HEAD_CLOSURE_RESULT_DELETED, style = MaterialTheme.typography.bodyLarge)
        QkVSpace(QkSpacing.Sm)
        Text(HEAD_CLOSURE_RESULT_THANKS, style = MaterialTheme.typography.bodyMedium, color = QkColor.Muted)
        QkVSpace(QkSpacing.Lg)
        QkButton(text = ACTION_CLOSURE_BACK, onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

// ---- 演示入口（预览用；路由接入由调用方决定） ----

/** 演示用四态切换器：状态与倒计时由调用方注入，组件零系统依赖。 */
@Preview(showBackground = true)
@Composable
private fun ClosureFlowPreview() {
    var step by rememberSaveable { mutableStateOf(0) }
    QkTheme {
        when (step) {
            0 -> ClosureApplyScreen(onNext = { step = 1 }, onReconsider = { step = 0 })
            1 -> ClosureVerifyScreen(
                resendSecondsLeft = 58,
                code = "",
                onCodeChange = {},
                onConfirm = { step = 2 },
            )
            2 -> ClosureProcessingScreen(
                coolingHoursLeft = 158,
                onRevoke = { step = 0 },
                onSimulateEnd = { step = 3 },
            )
            else -> ClosureResultScreen(onBack = { step = 0 })
        }
    }
}
