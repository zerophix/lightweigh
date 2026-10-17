package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.legal.AGREEMENT_SECTIONS
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.ui.legal.PRIVACY_SECTIONS
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * settings — 四件套之 Screen：账号三流程 + 法务入口（需求：恢复/退出/注销口径）。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 关于/清单/缓存（旧库 SettingsPages about/collect/share/清缓存 四分支）
    var extraSheet by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var cacheLabel by remember { mutableStateOf(cacheSizeLabel(context)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }
        QkCard(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                if (state.formal) "已登录：${state.phone}" else "游客使用中（数据只存本机）",
                style = MaterialTheme.typography.titleSmall,
            )
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (state.formal) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ActionRow("从云端恢复当前用户记录", enabled = !state.busy) { onIntent(SettingsIntent.Restore) }
                ActionRow("退出登录（本机与云端记录保留）", enabled = !state.busy) { onIntent(SettingsIntent.SignOutAsk) }
                ActionRow("注销账号（删除云端记录与登录身份）", enabled = !state.busy, danger = true) { onIntent(SettingsIntent.DeleteAsk) }
            }
        }
        QkCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("用户协议", enabled = true) { onIntent(SettingsIntent.OpenLegal(LegalDoc.AGREEMENT)) }
            ActionRow("隐私政策", enabled = true) { onIntent(SettingsIntent.OpenLegal(LegalDoc.PRIVACY)) }
        }
        QkCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("关于我们", enabled = true) { extraSheet = "about" }
            ActionRow("个人信息收集清单", enabled = true) { extraSheet = "collect" }
            ActionRow("第三方信息共享清单", enabled = true) { extraSheet = "share" }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ActionRow("一键清除缓存（当前 $cacheLabel）", enabled = true) { confirmClear = true }
            Text(
                "轻刻健康 v${appVersion(context)}（本地数据版）",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 关于
    if (extraSheet == "about") {
        AlertDialog(
            onDismissRequest = { extraSheet = null },
            title = { Text("关于我们") },
            text = {
                Column {
                    Text("应用：轻刻健康")
                    Text("版本：${appVersion(context)}", modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "轻刻健康配合 OKOK 协议体脂秤，在本机记录体重、体脂和日常健康数据。",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { extraSheet = null }) { Text("知道了") } },
        )
    }

    // 个人信息收集清单（旧库 CollectBody 逐行）
    if (extraSheet == "collect") {
        AlertDialog(
            onDismissRequest = { extraSheet = null },
            title = { Text("个人信息收集清单") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        "昵称、性别、出生日期、身高" to "计算体测指标",
                        "头像" to "个人展示",
                        "体重、阻抗、体脂" to "保存测量",
                        "饮食、运动、喝水等记录" to "日常打卡",
                        "体脂秤名称和 MAC" to "连接已绑定的秤",
                        "手机号" to "短信验证码登录",
                        "提问内容和当前体测摘要" to "登录后生成 AI 回答",
                    ).forEach { (name, use) ->
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            use,
                            modifier = Modifier.padding(bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (state.formal) {
                            "手机号和当前测量用户的记录会上传到 CloudBase。头像只在本机。"
                        } else {
                            "游客模式下，以上信息都只写在本机，不会上传。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { extraSheet = null }) { Text("知道了") } },
        )
    }

    // 第三方信息共享清单（旧库 ShareBody）
    if (extraSheet == "share") {
        AlertDialog(
            onDismissRequest = { extraSheet = null },
            title = { Text("第三方信息共享清单") },
            text = {
                Text(
                    if (state.formal) {
                        "手机号登录后，当前测量用户的档案、体脂秤、测量和健康记录会交给腾讯云开发 CloudBase 保存，" +
                            "只允许这个账号读写。头像不上传。AI 问答会把你的问题和当前这条体测摘要发给 CloudBase 的模型，" +
                            "密钥留在云端。食物拍照识别尚未接入。"
                    } else {
                        "游客不会把身体数据交给第三方，也不能使用 AI。手机号登录后，当前测量用户的记录会交给" +
                            "腾讯云开发 CloudBase 保存，提问时才会把问题和体测摘要发给模型。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { extraSheet = null }) { Text("知道了") } },
        )
    }

    // 清除缓存（旧库 :460-468：cacheDir + externalCacheDir，只清临时缓存）
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("一键清除缓存？") },
            text = { Text("只清除临时缓存。测量、档案和已绑定的体脂秤仍保留在本机。") },
            confirmButton = {
                TextButton(onClick = {
                    clearCache(context)
                    cacheLabel = cacheSizeLabel(context)
                    confirmClear = false
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    if (state.showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.SignOutCancel) },
            title = { Text("退出登录？") },
            text = { Text("只清除本机登录状态；本机测量与云端数据都保留。") },
            confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.SignOutConfirm) }) { Text("退出登录") } },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.SignOutCancel) }) { Text("取消") } },
        )
    }
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DeleteCancel) },
            title = { Text("注销账号") },
            text = {
                Column {
                    Text("将删除云端档案、体脂秤、测量与健康记录，且不可恢复；本机测量保留。")
                    QkTextField(
                        value = state.deleteCode,
                        onValueChange = { onIntent(SettingsIntent.DeleteCodeChanged(it)) },
                        label = "短信验证码",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.deleteCodeSent) onIntent(SettingsIntent.DeleteConfirm) else onIntent(SettingsIntent.DeleteSendCode)
                    },
                    enabled = !state.busy,
                ) {
                    Text(
                        when {
                            state.busy -> "处理中…"
                            state.deleteCodeSent -> "确认注销"
                            else -> "发送验证码"
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.DeleteCancel) }) { Text("取消") } },
        )
    }
    state.legalDoc.let { doc ->
        if (doc != LegalDoc.NONE) {
            val sections = if (doc == LegalDoc.AGREEMENT) AGREEMENT_SECTIONS else PRIVACY_SECTIONS
            AlertDialog(
                onDismissRequest = { onIntent(SettingsIntent.CloseLegal) },
                title = { Text(if (doc == LegalDoc.AGREEMENT) "用户协议" else "隐私政策") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        sections.forEach { (heading, body) ->
                            Text(heading, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.CloseLegal) }) { Text("知道了") } },
            )
        }
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"

private fun dirSize(dir: java.io.File?): Long {
    dir ?: return 0L
    val children = dir.listFiles() ?: return if (dir.exists()) dir.length() else 0L
    return children.sumOf { if (it.isDirectory) dirSize(it) else it.length() }
}

private fun cacheSizeLabel(context: android.content.Context): String {
    val bytes = listOfNotNull(context.cacheDir, context.externalCacheDir).sumOf { dirSize(it) }
    return "%.2fMB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
}

private fun clearCache(context: android.content.Context) {
    listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

@Composable
private fun ActionRow(title: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    QkTheme {
        SettingsScreen(state = SettingsUiState(loading = false, formal = true, phone = "13800138000"), onIntent = {}) {}
    }
}
