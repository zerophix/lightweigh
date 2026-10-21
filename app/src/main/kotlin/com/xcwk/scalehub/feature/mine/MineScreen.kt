@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkAvatar
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkMetric
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.components.QkWheelPicker
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.time.YearMonth

/**
 * mine — 四件套之 Screen：无状态渲染 + 事件转发。
 * 视觉只用主题槽与 Qk 组件（DEC-021 支柱5）；M4：头像（相册选图）与
 * 出生/身高滚轮（旧库 HeightSheet/BirthSheet 口径：年 1930..今年、月 01..12、
 * 日随月长、身高 100..230）。
 *
 * 版式按 v2 原型「我的」屏：资料卡（头像 + 昵称 + 一句引导）→ 我的健康档案卡
 * （性别胶囊、出生/身高/测量用户三行带箭头，编辑入口都在行内）→ 更多入口卡
 * （历史记录 / 设备管理 / 设置）。原型把编辑收进弹窗，本应用把编辑行直接铺开——
 * 少一层弹窗，行式语法与原型一致。
 */
@Composable
fun MineScreen(
    state: MineUiState,
    onIntent: (MineIntent) -> Unit,
    onOpenSettings: () -> Unit = {},
    newCameraTarget: () -> String? = { null },
    onOpenMembers: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDevice: () -> Unit = {},
    onOpenHabit: () -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        // 资料卡：点头像换图；昵称在档案卡内编辑（原型这里是只读展示 + 弹窗编辑）
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAvatarSheet = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkAvatar(path = state.avatarPath.ifBlank { null }, size = 54.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        state.nickname.ifBlank { "轻刻用户" },
                        style = MaterialTheme.typography.titleMedium,
                        color = QkColor.Fg,
                    )
                    Text(
                        "完善资料，获得更贴合的健康参考",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
                QkIcon(QkIcons.ChevronRight, size = 20.dp)
            }
        }

        if (state.unsetHint) {
            QkEmptyHint("未设身高，体脂估算会不准确——请先完善资料", modifier = Modifier.padding(top = QkSpacing.Sm))
        }

        QkSectionHead(title = "我的健康档案", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard {
            // 性别：胶囊行（原型 row-end 只读展示，这里保留即时切换）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "性别",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = QkColor.Fg,
                )
                QkChip("男", state.male) { onIntent(MineIntent.MaleChanged(true)) }
                QkChip("女", !state.male) { onIntent(MineIntent.MaleChanged(false)) }
            }
            QkDivider(modifier = Modifier.padding(vertical = 4.dp))
            // 测量用户：切换/添加/删除（旧库 Members 入口）
            QkListRow(
                title = "测量用户",
                subtitle = "切换当前测量的成员",
                icon = QkIcons.Account,
                glyphSize = 32.dp,
                onClick = onOpenMembers,
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
            // 出生年月：滚轮编辑（旧库 BirthSheet）
            QkListRow(
                title = "出生年月",
                subtitle = "用于年龄相关指标估算",
                icon = QkIcons.Period,
                glyphSize = 32.dp,
                onClick = { showBirthSheet = true },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            birthText(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                        QkIcon(QkIcons.ChevronRight, size = 18.dp)
                    }
                },
            )
            // 身高：滚轮编辑（旧库 HeightSheet）
            QkListRow(
                title = "身高",
                subtitle = "体脂与 BMI 估算依赖身高",
                icon = QkIcons.Height,
                glyphSize = 32.dp,
                onClick = { showHeightSheet = true },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.heightCm.takeIf { it.isNotBlank() }?.let { "$it 厘米" } ?: "未设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                        QkIcon(QkIcons.ChevronRight, size = 18.dp)
                    }
                },
            )
            QkDivider(modifier = Modifier.padding(vertical = 6.dp))
            QkTextField(
                value = state.nickname,
                onValueChange = { onIntent(MineIntent.NicknameChanged(it)) },
                label = "昵称（≤20 字，可空）",
                modifier = Modifier.padding(top = 6.dp),
            )
            if (state.summary.isNotBlank()) {
                Text(
                    state.summary,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            state.error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Danger,
                )
            }
            if (state.saved) {
                Text(
                    "已保存",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Primary,
                )
            }
            QkButton(
                text = if (state.saving) "保存中…" else "保存",
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                onClick = { onIntent(MineIntent.Save) },
            )
        }

        // 健康数据概览（原型 profile 页 metric-grid 三格；与首页体重卡同一事实源）
        if (state.hasOverview) {
            QkSectionHead(title = "健康数据概览", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QkMetric(
                        label = "体重",
                        value = state.overviewWeight,
                        unit = "kg",
                        modifier = Modifier.weight(1f),
                    )
                    QkMetric(
                        label = "体脂率",
                        value = state.overviewFat,
                        unit = "%",
                        modifier = Modifier.weight(1f),
                    )
                    QkMetric(
                        label = "BMI",
                        value = state.overviewBmi,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 入口卡（原型 mine 页第三张卡）
        QkSectionHead(title = "更多", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard {
            QkListRow(
                title = "历史记录",
                subtitle = "查看全部测量数据",
                icon = QkIcons.History,
                glyphSize = 32.dp,
                onClick = onOpenHistory,
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
            QkListRow(
                title = "设备管理",
                subtitle = "连接与管理体脂秤",
                icon = QkIcons.Device,
                glyphSize = 32.dp,
                onClick = onOpenDevice,
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
            QkListRow(
                title = "设置",
                subtitle = "隐私、安全与应用信息",
                icon = QkIcons.Setting,
                glyphSize = 32.dp,
                showDivider = false,
                onClick = onOpenSettings,
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
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
        MineScreen(state = MineUiState(loading = false, summary = "男 | --岁 | --cm", unsetHint = true), onIntent = {}, onOpenSettings = {})
    }
}
