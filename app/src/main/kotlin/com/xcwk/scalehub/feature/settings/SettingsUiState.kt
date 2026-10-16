package com.xcwk.scalehub.feature.settings

import com.xcwk.scalehub.ui.legal.LegalDoc

/** settings — 四件套之 UiState（账号区三流程 + 法务入口）。 */
data class SettingsUiState(
    val loading: Boolean = true,
    val formal: Boolean = false,
    val phone: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val showSignOutConfirm: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleteCode: String = "",
    val deleteCodeSent: Boolean = false,
    val legalDoc: LegalDoc = LegalDoc.NONE,
)

sealed interface SettingsIntent {
    data object Restore : SettingsIntent          // 从云端恢复当前用户记录
    data object SignOutAsk : SettingsIntent
    data object SignOutCancel : SettingsIntent
    data object SignOutConfirm : SettingsIntent
    data object DeleteAsk : SettingsIntent
    data object DeleteCancel : SettingsIntent
    data class DeleteCodeChanged(val value: String) : SettingsIntent
    data object DeleteSendCode : SettingsIntent
    data object DeleteConfirm : SettingsIntent
    data class OpenLegal(val doc: LegalDoc) : SettingsIntent
    data object CloseLegal : SettingsIntent
}
