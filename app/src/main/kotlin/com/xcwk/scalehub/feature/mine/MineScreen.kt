@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkAvatar
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkWheelPicker
import com.xcwk.scalehub.ui.theme.QkTheme
import java.time.YearMonth

/**
 * mine — 四件套之 Screen：我的资料表单，无状态渲染 + 事件转发。
 * 视觉只用主题槽与 Qk 组件（DEC-021 支柱5）；M4：头像（相册选图）与
 * 出生/身高滚轮（旧库 HeightSheet/BirthSheet 口径：年 1930..今年、月 01..12、
 * 日随月长、身高 100..230）。
 */
@Composable
fun MineScreen(
    state: MineUiState,
    onIntent: (MineIntent) -> Unit,
    onOpenSettings: () -> Unit = {},
    newCameraTarget: () -> String? = { null },
) {
    var showAvatarSheet by rememberSaveable { mutableStateOf(false) }
    var showBirthSheet by rememberSaveable { mutableStateOf(false) }
    var showHeightSheet by rememberSaveable { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onIntent(MineIntent.AvatarChosen(it.toString())) }
    }
    // 相机头像：先取 FileProvider 目标 URI 交给系统相机写入，成功后再压缩落盘
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingCameraUri?.let { onIntent(MineIntent.AvatarChosen(it)) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的资料", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text(
                "设置",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(4.dp),
            )
        }
        if (state.unsetHint) {
            QkEmptyHint("未设身高，体脂估算会不准确——请先完善资料", modifier = Modifier.padding(top = 4.dp))
        }
        QkCard(modifier = Modifier.padding(top = 16.dp)) {
            // 头像行（旧库「头像」入口 :408-410）
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAvatarSheet = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("头像", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                QkAvatar(path = state.avatarPath.ifBlank { null }, size = 48.dp)
            }
            OutlinedTextField(
                value = state.nickname,
                onValueChange = { onIntent(MineIntent.NicknameChanged(it)) },
                label = { Text("昵称（≤20 字，可空）") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                QkChip("男", state.male) { onIntent(MineIntent.MaleChanged(true)) }
                QkChip("女", !state.male) { onIntent(MineIntent.MaleChanged(false)) }
            }
            // 出生年月：滚轮编辑（旧库 BirthSheet）
            PickerRow(
                label = "出生年月",
                value = birthText(state),
                onClick = { showBirthSheet = true },
            )
            // 身高：滚轮编辑（旧库 HeightSheet）
            PickerRow(
                label = "身高",
                value = state.heightCm.takeIf { it.isNotBlank() }?.let { "$it 厘米" } ?: "未设置",
                onClick = { showHeightSheet = true },
            )
        }
        if (state.summary.isNotBlank()) {
            Text(state.summary, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        if (state.saved) {
            Text("已保存", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = { onIntent(MineIntent.Save) },
            enabled = !state.saving && !state.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(if (state.saving) "保存中…" else "保存") }
    }

    // 修改头像：拍照 / 相册（旧库面板两分支）
    if (showAvatarSheet) {
        AlertDialog(
            onDismissRequest = { showAvatarSheet = false },
            title = { Text("修改头像") },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarSheet = false
                    newCameraTarget()?.let { target ->
                        pendingCameraUri = target
                        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, android.net.Uri.parse(target))
                        }
                        cameraLauncher.launch(intent)
                    }
                }) { Text("拍照") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showAvatarSheet = false
                        imagePicker.launch("image/*")
                    }) { Text("从相册中选择") }
                    TextButton(onClick = { showAvatarSheet = false }) { Text("取消") }
                }
            },
        )
    }

    // 出生年月滚轮（年 1930..今年；日随月长，保存前按所选年月收敛）
    if (showBirthSheet) {
        val thisYear = java.time.LocalDate.now().year
        val initialYear = state.birthYear.toIntOrNull()?.coerceIn(1930, thisYear) ?: thisYear
        val initialMonth = state.birthMonth.toIntOrNull()?.coerceIn(1, 12) ?: 1
        val initialDay = state.birthDay.toIntOrNull()?.coerceIn(1, 31) ?: 1
        var pickedYear by rememberSaveable { mutableStateOf(initialYear) }
        var pickedMonth by rememberSaveable { mutableStateOf(initialMonth) }
        var pickedDay by rememberSaveable { mutableStateOf(initialDay) }
        val dayCount = YearMonth.of(pickedYear, pickedMonth).lengthOfMonth()
        val shownDay = pickedDay.coerceAtMost(dayCount)
        AlertDialog(
            onDismissRequest = { showBirthSheet = false },
            title = { Text("编辑出生年月") },
            text = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    QkWheelPicker(
                        labels = (1930..thisYear).map { "$it 年" },
                        initialIndex = pickedYear - 1930,
                        modifier = Modifier.weight(1.2f),
                    ) { pickedYear = 1930 + it }
                    QkWheelPicker(
                        labels = (1..12).map { "%02d 月".format(it) },
                        initialIndex = pickedMonth - 1,
                        modifier = Modifier.weight(1f),
                    ) { pickedMonth = it + 1 }
                    QkWheelPicker(
                        labels = (1..dayCount).map { "%02d 日".format(it) },
                        initialIndex = shownDay - 1,
                        modifier = Modifier.weight(1f),
                    ) { pickedDay = it + 1 }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(MineIntent.BirthPicked(pickedYear, pickedMonth, pickedDay.coerceAtMost(YearMonth.of(pickedYear, pickedMonth).lengthOfMonth())))
                    showBirthSheet = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showBirthSheet = false }) { Text("取消") } },
        )
    }

    // 身高滚轮（100..230，同旧库量程）
    if (showHeightSheet) {
        val initialHeight = state.heightCm.toIntOrNull()?.coerceIn(100, 230) ?: 170
        var pickedHeight by rememberSaveable { mutableStateOf(initialHeight) }
        AlertDialog(
            onDismissRequest = { showHeightSheet = false },
            title = { Text("编辑身高") },
            text = {
                QkWheelPicker(
                    labels = (100..230).map { it.toString() },
                    initialIndex = initialHeight - 100,
                ) { pickedHeight = 100 + it }
            },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(MineIntent.HeightPicked(pickedHeight))
                    showHeightSheet = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showHeightSheet = false }) { Text("取消") } },
        )
    }
}

private fun birthText(state: MineUiState): String {
    val y = state.birthYear.toIntOrNull()
    val m = state.birthMonth.toIntOrNull()
    val d = state.birthDay.toIntOrNull()
    return if (y != null && m != null && d != null) {
        String.format(java.util.Locale.US, "%d年%02d月%02d日", y, m, d)
    } else {
        "未设置"
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun MineScreenPreview() {
    QkTheme {
        MineScreen(
            state = MineUiState(
                loading = false,
                male = true,
                birthYear = "1996", birthMonth = "5", birthDay = "20",
                heightCm = "175", nickname = "测试",
                summary = "男 | 30岁 | 175cm",
                unsetHint = false,
            ),
            onIntent = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MineScreenUnsetPreview() {
    QkTheme {
        MineScreen(
            state = MineUiState(loading = false, summary = "男 | --岁 | --cm", unsetHint = true),
            onIntent = {},
            onOpenSettings = {},
        )
    }
}
