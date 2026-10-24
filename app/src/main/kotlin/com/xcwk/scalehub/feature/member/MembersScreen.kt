package com.xcwk.scalehub.feature.member

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.domain.model.Member
import com.xcwk.scalehub.ui.components.QkAvatar
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.shell.WiringEntries
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * member — 四件套之 Screen：测量用户列表（旧库 Members.kt 移植）。
 * 行=头像+昵称+秤上编号+当前勾选；多于一人可删；满 8 人显示上限提示。
 */
@Composable
fun MembersRoute(vm: MembersViewModel = viewModel(factory = MembersViewModel.Factory), onBack: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    MembersScreen(state = state, avatarOf = vm::avatarOf, onIntent = vm::onIntent, onBack = onBack)
}

@Composable
fun MembersScreen(
    state: MembersUiState,
    avatarOf: (String) -> String,
    onIntent: (MembersIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    // 接入波第四批：家庭邀请六屏（T-088）从本页进入，返回即回成员列表
    var familyPage by remember { mutableStateOf<FamilySharePage?>(null) }
    familyPage?.let { page ->
        FamilyShareRoute(initialPage = page, onBack = { familyPage = null })
        return
    }

    // 接入波：行动计划（T-087）从本页进入，返回即回成员列表
    var showPlans by remember { mutableStateOf(false) }
    if (showPlans) {
        ActionPlansRoute(onBack = { showPlans = false })
        return
    }

    // T-086：资料编辑 / 测量记录空态 / 设备管理空态，返回即回成员列表
    var memberPage by remember { mutableStateOf(MemberPage.List) }
    val viewingName = state.members.firstOrNull { it.id == state.activeId }?.nickname.orEmpty()
    when (memberPage) {
        MemberPage.Profile -> {
            MemberProfileRoute(memberName = viewingName, onBack = { memberPage = MemberPage.List })
            return
        }
        MemberPage.NoRecord -> {
            MemberNoRecordScreen(
                memberName = viewingName,
                onBack = { memberPage = MemberPage.List },
                onMeasure = { memberPage = MemberPage.List },
            )
            return
        }
        MemberPage.NoDevice -> {
            MemberNoDeviceScreen(
                memberName = viewingName,
                onBack = { memberPage = MemberPage.List },
                onMeasure = { memberPage = MemberPage.List },
            )
            return
        }
        MemberPage.List -> Unit
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text("测量用户", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "称重时下发当前用户的身高和年龄。体脂秤仍是共用的。",
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 接入波入口：行动计划六态（计划列表 / 编辑 / 提醒 / 空态 / 通知被拒）
        QkCard(modifier = Modifier.padding(bottom = 8.dp)) {
            QkListRow(
                title = WiringEntries.ActionPlans.title,
                subtitle = WiringEntries.ActionPlans.hint,
                showDivider = false,
                onClick = { showPlans = true },
            )
        }
        // T-086 入口：当前成员的测量记录空态 / 设备管理空态
        QkCard(modifier = Modifier.padding(bottom = 8.dp)) {
            QkListRow(
                title = MemberProfileText.NavRecord,
                subtitle = "当前成员还没有测量记录时的引导",
                onClick = { memberPage = MemberPage.NoRecord },
            )
            QkListRow(
                title = MemberProfileText.NavDevice,
                subtitle = "成员还没有上过秤时的引导",
                showDivider = false,
                onClick = { memberPage = MemberPage.NoDevice },
            )
        }
        // 接入波第四批入口：家庭邀请六屏（入口文案见 FamilyShareRoute 的 FamilyShareEntry）
        QkCard(modifier = Modifier.padding(bottom = 8.dp)) {
            QkListRow(
                title = FamilyShareEntry.INVITE_TITLE,
                subtitle = FamilyShareEntry.INVITE_HINT,
                onClick = { familyPage = FamilySharePage.Invite },
            )
            QkListRow(
                title = FamilyShareEntry.ACCEPT_TITLE,
                subtitle = FamilyShareEntry.ACCEPT_HINT,
                onClick = { familyPage = FamilySharePage.Accept },
            )
            QkListRow(
                title = FamilyShareEntry.REVOKE_TITLE,
                subtitle = FamilyShareEntry.REVOKE_HINT,
                showDivider = false,
                onClick = { familyPage = FamilySharePage.Revoke },
            )
        }
        QkCard {
            state.members.forEach { member ->
                val current = member.id == state.activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(MembersIntent.Switch(member.id)) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QkAvatar(path = avatarOf(member.id).ifBlank { null }, size = 40.dp)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(member.nickname.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "秤上的用户编号 ${member.slot}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (current) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "当前用户",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑资料",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = QkSpacing.Sm)
                                .size(22.dp)
                                .clickable { memberPage = MemberPage.Profile },
                        )
                    }
                    if (state.members.size > 1) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(22.dp)
                                .clickable { onIntent(MembersIntent.AskDelete(member)) },
                        )
                    }
                }
            }
            if (!state.maxReached) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onIntent(MembersIntent.AskAdd) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        "添加用户",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Text(
                    "最多 ${Member.MAX} 个测量用户",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.message?.let {
            Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.primary)
        }
    }

    if (state.showAddDialog) {
        var name by remember { mutableStateOf(state.addName) }
        AlertDialog(
            onDismissRequest = { onIntent(MembersIntent.CancelAdd) },
            title = { Text("添加用户") },
            text = {
                QkTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    placeholder = "昵称",
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(MembersIntent.AddNameChanged(name))
                    onIntent(MembersIntent.ConfirmAdd)
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { onIntent(MembersIntent.CancelAdd) }) { Text("取消") } },
        )
    }

    state.pendingDelete?.let { removing ->
        AlertDialog(
            onDismissRequest = { onIntent(MembersIntent.CancelDelete) },
            title = { Text("删除用户") },
            text = {
                Text(
                    "会删除「${removing.nickname.ifBlank { "未命名" }}」在这台手机上的档案与头像；" +
                        "其测量与健康记录不再显示（本机保留）。已绑定的体脂秤仍保留。",
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(MembersIntent.ConfirmDelete) }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(MembersIntent.CancelDelete) }) { Text("取消") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MembersScreenPreview() {
    QkTheme {
        MembersScreen(
            state = MembersUiState(
                loading = false,
                members = listOf(Member("1", 1, "我"), Member("2", 2, "家人")),
                activeId = "1",
            ),
            avatarOf = { "" },
            onIntent = {},
        )
    }
}
