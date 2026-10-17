package com.xcwk.scalehub.feature.member

import com.xcwk.scalehub.domain.model.Member

/** member — 四件套之 UiState（旧库 Members.kt 语义：切换/添加/删除）。 */
data class MembersUiState(
    val loading: Boolean = true,
    val members: List<Member> = emptyList(),
    val activeId: String = "",
    val maxReached: Boolean = false,
    val showAddDialog: Boolean = false,
    val addName: String = "",
    val pendingDelete: Member? = null,
    val deleting: Boolean = false,
    val message: String? = null,
)

sealed interface MembersIntent {
    data class Switch(val id: String) : MembersIntent
    data object AskAdd : MembersIntent
    data object CancelAdd : MembersIntent
    data class AddNameChanged(val value: String) : MembersIntent
    data object ConfirmAdd : MembersIntent
    data class AskDelete(val member: Member) : MembersIntent
    data object CancelDelete : MembersIntent
    data object ConfirmDelete : MembersIntent
}
