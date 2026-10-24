package com.xcwk.scalehub.feature.mine

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.App
import com.xcwk.scalehub.feature.auth.AccountBoundaryView
import com.xcwk.scalehub.feature.auth.AuthBoundaryHost
import com.xcwk.scalehub.feature.auth.maskedPhone
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.legal.AGREEMENT_SECTIONS
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.ui.legal.PRIVACY_SECTIONS
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * mine — 二级页接入装配点（T-050 / T-089 / T-090 / T-101 四批交付屏的接线）。
 *
 * 四个屏此前只有呈现层，我的页上没有入口，用户点不到。本文件把「我的页那一行 →
 * 哪一屏 → 返回去哪」写在一处：
 *
 * ```
 * 我的 · 账号与信息 ─┬─ 账号与安全 ────── AccountSecurityScreen ─┬─ 注销账号 → 注销四态流程
 *                    ├─ 隐私与数据管理 ── PrivacyDetailScreen
 *                    ├─ 健康数据说明 ──── HealthInfoScreen（auth 模块）
 *                    └─ 关于轻衡 ──────── AboutScreen
 * ```
 *
 * ## 数据来源与仍是空回调的地方（诚实标注）
 * - 手机号取会话真实值（[com.xcwk.scalehub.application.auth.AuthPort.phone]）并掩码；
 *   未登录如实显示「未登录」，不编造号码。
 * - 邮箱/密码态与三行的「更换/绑定/修改」：账户接口未提供，点击给明确反馈而不是假装成功。
 * - 注销：验证码经 [com.xcwk.scalehub.application.auth.AuthService.sendDeleteCode] 真实下发；
 *   后端没有「预约注销」接口，7 天冷静期由本机持有，「模拟冷静期结束」那一刻才调
 *   [com.xcwk.scalehub.application.sync.DeleteAccountService] 真正注销。
 * - 隐私页「删除全部健康数据」：应用层只有单条删除用例，无批量删除，点击给明确反馈。
 */

// ---- 注销四态流程（纯函数，JVM 单测钉死推进表） ----

/** 注销流程四态（原型 07 v-closure-apply / verify / processing / result）。 */
enum class ClosureStep { Apply, Verify, Processing, Result }

/** 注销流程事件：只表达「去哪」，验证码与冷静期留在宿主状态里。 */
sealed interface ClosureEvent {
    /** 申请页「我已了解，下一步」→ 验证。 */
    data object Next : ClosureEvent

    /** 验证页「确认注销」→ 处理中（冷静期）。 */
    data object Confirm : ClosureEvent

    /** 冷静期结束 → 结果页（真实注销调用成功后才推到这里）。 */
    data object CoolDownEnded : ClosureEvent
}

/** 纯推进函数：只允许逐级前进，越级/回退事件原地不动。 */
fun closureStepReduce(step: ClosureStep, event: ClosureEvent): ClosureStep = when (event) {
    ClosureEvent.Next -> if (step == ClosureStep.Apply) ClosureStep.Verify else step
    ClosureEvent.Confirm -> if (step == ClosureStep.Verify) ClosureStep.Processing else step
    ClosureEvent.CoolDownEnded -> if (step == ClosureStep.Processing) ClosureStep.Result else step
}

/** 冷静期时长（原型原文「7 天冷静期」）。 */
const val CLOSURE_COOLING_HOURS = 168L

/** 账号安全页手机号绑定态的兜底文案：未登录时不编造掩码号码。 */
const val ACCOUNT_PHONE_UNBOUND = "未登录"

/** 登录密码绑定态的兜底文案（账户接口未提供密码态）。 */
const val ACCOUNT_PASSWORD_UNSET = "未设置"

/** 验证码重发冷却（秒），与 auth 侧发码倒计时口径一致。 */
private const val CLOSURE_RESEND_SECONDS = 60

/** 手机号掩码：只保留数字并按 [maskedPhone] 收敛；空号码回落到「未登录」。 */
fun accountPhoneMasked(phone: String): String {
    var digits = phone.filter { it.isDigit() }
    // 会话可能存成 +86 前缀的国际形态：掩码前先剥掉，避免掩成 861****0000
    if (digits.length == 13 && digits.startsWith("86")) digits = digits.removePrefix("86")
    return if (digits.isBlank()) ACCOUNT_PHONE_UNBOUND else maskedPhone(digits)
}

/** 关于页版本号形态：gradle versionName `0.1.0` → 原型 `v0.1.0`。 */
fun aboutVersionLabel(raw: String): String = if (raw.startsWith("v")) raw else "v$raw"

/** 应用版本号（versionName）；读不到时回落到 gradle 里的默认值。 */
fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "0.1.0"

// ---- 宿主 ----

/** 我的页「账号与信息」卡：四行入口（顺序即 [MINE_ENTRIES]）。 */
@Composable
fun MineEntriesCard(
    onOpenEntry: (MineEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    QkSectionHead(title = MINE_ENTRIES_SECTION, modifier = modifier.padding(top = QkSpacing.Lg))
    QkCard {
        MINE_ENTRIES.forEachIndexed { index, entry ->
            QkListRow(
                title = entry.title,
                subtitle = entry.hint,
                showDivider = index != MINE_ENTRIES.lastIndex,
                onClick = { onOpenEntry(entry) },
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
        }
    }
}

/**
 * 二级页宿主：系统返回键先退二级页，不直接落到页签切换。
 *
 * @param entry 我的页点进来的那一行
 * @param onBack 回到我的页
 */
@Composable
fun MineMoreHost(
    entry: MineEntry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    when (entry) {
        MineEntry.AccountSecurity -> AccountSecurityHost(onBack = onBack, modifier = modifier)
        MineEntry.PrivacyDetail -> PrivacyDetailHost(onBack = onBack, modifier = modifier)
        // 健康数据说明是 auth 模块的边界屏（原型 14 v-healthinfo），自带返回头
        MineEntry.HealthInfo -> AuthBoundaryHost(
            view = AccountBoundaryView.HealthInfo,
            onDismiss = onBack,
            modifier = modifier,
        )

        MineEntry.About -> AboutHost(onBack = onBack, modifier = modifier)
    }
}

/** 二级页版式：返回圆钮 + 呈现式屏（屏自己带标题，这里不再叠一层）。 */
@Composable
private fun MinePageFrame(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg)) {
            QkBackButton(onClick = onBack)
        }
        content()
    }
}

@Composable
private fun AccountSecurityHost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var closureOpen by rememberSaveable { mutableStateOf(false) }

    if (closureOpen) {
        ClosureHost(onBack = { closureOpen = false }, modifier = modifier)
        return
    }

    MinePageFrame(onBack = onBack, modifier = modifier) {
        AccountSecurityScreen(
            phoneMasked = accountPhoneMasked(app.container.authPort.phone()),
            emailState = EMAIL_UNBOUND,
            passwordState = if (app.container.authPort.isFormalAccount()) PASSWORD_SET else ACCOUNT_PASSWORD_UNSET,
            onRowClick = { row ->
                Toast.makeText(context, "$row：账户接口未接入，暂不能修改", Toast.LENGTH_SHORT).show()
            },
            onClosureClick = { closureOpen = true },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PrivacyDetailHost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    MinePageFrame(onBack = onBack, modifier = modifier) {
        PrivacyDetailScreen(
            deleteDialogOpen = dialogOpen,
            onRowClick = { row ->
                Toast.makeText(context, "$row：数据层未接入，暂不能打开", Toast.LENGTH_SHORT).show()
            },
            onDeleteAll = { dialogOpen = true },
            onConfirmDelete = {
                dialogOpen = false
                // 应用层只有单条删除用例，没有「删除全部」；不假装删过
                Toast.makeText(context, "批量删除待数据层提供用例", Toast.LENGTH_SHORT).show()
            },
            onCancelDelete = { dialogOpen = false },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutHost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var legal by rememberSaveable { mutableStateOf(LegalDoc.NONE) }
    val version = remember(context) { aboutVersionLabel(appVersionName(context)) }

    MinePageFrame(onBack = onBack, modifier = modifier) {
        AboutScreen(
            version = version,
            onRowClick = { row ->
                when (row) {
                    ROW_USER_AGREEMENT -> legal = LegalDoc.AGREEMENT
                    ROW_PRIVACY_POLICY -> legal = LegalDoc.PRIVACY
                    else -> Toast.makeText(context, "第三方 SDK 清单待接入", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }

    if (legal != LegalDoc.NONE) {
        val sections = if (legal == LegalDoc.AGREEMENT) AGREEMENT_SECTIONS else PRIVACY_SECTIONS
        AlertDialog(
            onDismissRequest = { legal = LegalDoc.NONE },
            title = { Text(if (legal == LegalDoc.AGREEMENT) ROW_USER_AGREEMENT else ROW_PRIVACY_POLICY) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sections.forEach { (heading, body) ->
                        Text(heading, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { legal = LegalDoc.NONE }) { Text("知道了") }
            },
        )
    }
}

/** 注销四态宿主：发码与注销都是真实调用，冷静期由本机持有。 */
@Composable
private fun ClosureHost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(ClosureStep.Apply) }
    var code by rememberSaveable { mutableStateOf("") }
    var resendSeconds by rememberSaveable { mutableStateOf(0) }

    // 进入验证页即真实下发注销验证码（发送失败按服务端文案提示）
    LaunchedEffect(step) {
        if (step != ClosureStep.Verify) return@LaunchedEffect
        val error = app.container.authService.sendDeleteCode()
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        } else {
            resendSeconds = CLOSURE_RESEND_SECONDS
        }
    }

    // 重发冷却：只驱动「N 秒后可重发」文案
    LaunchedEffect(step, resendSeconds) {
        if (step == ClosureStep.Verify && resendSeconds > 0) {
            delay(1_000)
            resendSeconds -= 1
        }
    }

    when (step) {
        ClosureStep.Apply -> MinePageFrame(onBack = onBack, modifier = modifier) {
            ClosureApplyScreen(
                onNext = { step = closureStepReduce(step, ClosureEvent.Next) },
                onReconsider = onBack,
                modifier = Modifier.weight(1f),
            )
        }

        ClosureStep.Verify -> MinePageFrame(onBack = { step = ClosureStep.Apply }, modifier = modifier) {
            ClosureVerifyScreen(
                resendSecondsLeft = resendSeconds,
                code = code,
                onCodeChange = { code = it },
                onConfirm = {
                    Toast.makeText(context, TITLE_CLOSURE_SUBMITTED, Toast.LENGTH_SHORT).show()
                    step = closureStepReduce(step, ClosureEvent.Confirm)
                },
                modifier = Modifier.weight(1f),
            )
        }

        ClosureStep.Processing -> MinePageFrame(onBack = onBack, modifier = modifier) {
            ClosureProcessingScreen(
                coolingHoursLeft = CLOSURE_COOLING_HOURS,
                onRevoke = {
                    Toast.makeText(context, "已撤销注销，账号恢复", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                onSimulateEnd = {
                    // 冷静期结束这一刻才真正注销；失败留在本页并按服务端文案提示
                    scope.launch {
                        val outcome = app.container.deleteAccountService.execute(code)
                        if (outcome.ok) {
                            step = closureStepReduce(step, ClosureEvent.CoolDownEnded)
                        } else {
                            Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        ClosureStep.Result -> ClosureResultScreen(
            onBack = onBack,
            modifier = modifier.fillMaxSize(),
        )
    }
}
