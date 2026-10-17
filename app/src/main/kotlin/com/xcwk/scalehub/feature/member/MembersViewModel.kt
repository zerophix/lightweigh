package com.xcwk.scalehub.feature.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.domain.model.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * member — ViewModel：测量用户管理（旧库 MemberStore UI 语义）。
 * 切换/增删经容器口落库并广播 DataRefreshBus（替代旧库 UiRefresh.bump）。
 * 删除口径声明：v2 删除成员即从列表消失（其测量随 member_id 留档不可见），
 * 档案键与头像文件联动删除；旧库文案中的「删除测量与健康记录」按 v2 语义改写。
 */
class MembersViewModel(
    private val memberList: () -> List<Member>,
    private val activeMemberId: () -> String,
    private val switchMember: (String) -> Unit,
    private val addMember: (String) -> Boolean,
    private val removeMember: (String) -> Boolean,
    private val memberAvatarPath: (String) -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun avatarOf(id: String): String = memberAvatarPath(id)

    fun onIntent(intent: MembersIntent) {
        when (intent) {
            is MembersIntent.Switch -> {
                if (intent.id != _state.value.activeId) {
                    switchMember(intent.id)
                    refresh()
                    _state.update { it.copy(message = "已切换测量用户") }
                }
            }
            MembersIntent.AskAdd -> _state.update { it.copy(showAddDialog = true, addName = "", message = null) }
            MembersIntent.CancelAdd -> _state.update { it.copy(showAddDialog = false) }
            is MembersIntent.AddNameChanged -> _state.update { it.copy(addName = intent.value.take(20)) }
            MembersIntent.ConfirmAdd -> {
                val created = addMember(_state.value.addName)
                refresh()
                _state.update {
                    it.copy(
                        showAddDialog = false,
                        message = if (created) "已添加并切换" else "最多 ${Member.MAX} 个测量用户",
                    )
                }
            }
            is MembersIntent.AskDelete -> _state.update { it.copy(pendingDelete = intent.member, message = null) }
            MembersIntent.CancelDelete -> _state.update { it.copy(pendingDelete = null) }
            MembersIntent.ConfirmDelete -> {
                val target = _state.value.pendingDelete ?: return
                removeMember(target.id)
                refresh()
                _state.update { it.copy(pendingDelete = null, deleting = false, message = "已删除「${target.nickname.ifBlank { "未命名" }}」") }
            }
        }
    }

    private fun refresh() {
        val members = memberList()
        _state.update {
            it.copy(
                loading = false,
                members = members,
                activeId = activeMemberId(),
                maxReached = members.size >= Member.MAX,
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MembersViewModel(
                    memberList = app.container.memberList,
                    activeMemberId = app.container.activeMemberId,
                    switchMember = app.container.switchMember,
                    addMember = app.container.addMember,
                    removeMember = app.container.removeMember,
                    memberAvatarPath = app.container.memberAvatarPath,
                )
            }
        }
    }
}
