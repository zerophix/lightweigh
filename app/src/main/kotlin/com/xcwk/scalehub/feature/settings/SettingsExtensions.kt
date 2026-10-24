package com.xcwk.scalehub.feature.settings

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.application.format.QkFormat
import com.xcwk.scalehub.domain.model.LengthUnit
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit
import com.xcwk.scalehub.feature.privacy.PrivacyAuthEntry
import com.xcwk.scalehub.feature.privacy.PrivacyAuthRoute
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * unit-settings — 单位设置页（DEC-043-C，参考 UnitSettings.kt:37）。
 * 重量 kg/斤/磅、长度 cm/in。
 */
@Composable
fun UnitSettingsScreen(
    onBack: () -> Unit = {},
    vm: UnitSettingsViewModel = viewModel(factory = UnitSettingsViewModel.Factory),
) {
    val prefs by vm.unitPrefs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(title = "单位设置", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        QkSectionHead(title = "重量单位", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            QkFormat.allWeightUnits.forEachIndexed { i, unit ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = prefs.weightUnit == unit,
                        onClick = { vm.onWeightUnitSelected(unit) }
                    )
                    Text(
                        "${unit.symbolZh} (${unit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Fg,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                }
            }
        }

        QkSectionHead(title = "长度单位", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            QkFormat.allLengthUnits.forEachIndexed { i, unit ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = prefs.lengthUnit == unit,
                        onClick = { vm.onLengthUnitSelected(unit) }
                    )
                    Text(
                        "${unit.symbolZh} (${unit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Fg,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                }
            }
        }

        QkSectionHead(title = "衍生指标单位", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BMI、体脂率等衍生指标随单位切换",
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Fg,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = prefs.applyToDerived,
                    onCheckedChange = { vm.onApplyToDerivedChanged(it) }
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

/** 单位设置 ViewModel */
class UnitSettingsViewModel(
    private val profileReader: com.xcwk.scalehub.domain.repository.MemberProfileReader,
    private val profileWriter: com.xcwk.scalehub.domain.repository.MemberProfileWriter,
) : ViewModel() {

    private val _unitPrefs = MutableStateFlow(profileReader.unitPreferences())
    val unitPrefs: StateFlow<UserUnitPreferences> = _unitPrefs

    fun onWeightUnitSelected(unit: WeightUnit) {
        val newPrefs = _unitPrefs.value.copy(weightUnit = unit)
        _unitPrefs.value = newPrefs
        savePrefs(newPrefs)
    }

    fun onLengthUnitSelected(unit: LengthUnit) {
        val newPrefs = _unitPrefs.value.copy(lengthUnit = unit)
        _unitPrefs.value = newPrefs
        savePrefs(newPrefs)
    }

    fun onApplyToDerivedChanged(apply: Boolean) {
        val newPrefs = _unitPrefs.value.copy(applyToDerived = apply)
        _unitPrefs.value = newPrefs
        savePrefs(newPrefs)
    }

    private fun savePrefs(prefs: UserUnitPreferences) {
        viewModelScope.launch {
            profileWriter.saveUnitPreferences(prefs)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as com.xcwk.scalehub.App
                UnitSettingsViewModel(
                    app.container.profileReader,
                    app.container.profileWriter,
                )
            }
        }
    }
}

/**
 * privacy-settings — 隐私设置页（DEC-043-I，参考 SettingsPages.kt:319-330）。
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit = {},
) {
    // 接入波第四批：健康数据授权五态（T-096）覆盖本页，返回即回隐私设置
    var showAuthFlow by remember { mutableStateOf(false) }
    if (showAuthFlow) {
        PrivacyAuthRoute(onBack = { showAuthFlow = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(title = "隐私设置", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        QkSectionHead(title = "数据收集", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            PrivacyRow("身体数据", "体重、体脂率等测量数据仅保存在本机")
            PrivacyRow("头像照片", "头像仅保存在本机，不上传云端")
            PrivacyRow("健康记录", "健康日志数据仅保存在本机")
        }

        QkSectionHead(title = "数据共享", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            PrivacyRow("AI 分析", "AI 解读需要发送身体数据到云端，仅正式账号可用")
            PrivacyRow("云同步", "登录后数据同步到云端，用于多设备同步")
            QkListRow(
                title = PrivacyAuthEntry.TITLE,
                subtitle = PrivacyAuthEntry.HINT,
                showDivider = false,
                onClick = { showAuthFlow = true },
                trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun PrivacyRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
        Text(
            subtitle,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/**
 * sync-status — 同步状态详情页（DEC-043-I，参考 SettingsPages.kt:389-420）。
 */
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit = {},
    lastSyncTime: String? = null,
    syncEnabled: Boolean = true,
    onSyncNow: () -> Unit = {},
    onToggleSync: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(title = "同步状态", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        QkSectionHead(title = "云同步", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("自动同步", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
                Switch(checked = syncEnabled, onCheckedChange = onToggleSync)
            }
            Text(
                "上次同步：${lastSyncTime ?: "从未同步"}",
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}