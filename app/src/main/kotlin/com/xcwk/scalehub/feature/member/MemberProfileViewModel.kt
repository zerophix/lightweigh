package com.xcwk.scalehub.feature.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.UpdateProfileCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * member — 成员资料编辑的 ViewModel（T-086）：把 [reduceMemberProfile] 纯状态机接到真实端口。
 *
 * ## 端口
 * - 读：`container.profileReader`（本机档案真源，键按**当前成员**隔离，故本页只编辑当前成员）；
 * - 写：`container.updateProfileService`（走 MemberProfile 构造守卫与 DataChange.Profile 广播）。
 *
 * ## 失败语义
 * 保存抛异常（网络/落库/构造守卫）一律折算成 `SaveResult(false)` → 失败页，
 * 草稿原样保留，点「重试保存」用同一份草稿重发（AC-J1-13 保存失败可重试）。
 *
 * ## 不落库的字段（明确边界，不伪称已实现）
 * - 体重目标：本机档案没有该字段，只参与表单校验；
 * - 「不告知」性别：档案只有 `male: Boolean`，选「不告知」时沿用原值，不改性别。
 */
class MemberProfileViewModel(
    private val readDraft: () -> MemberProfileDraft,
    private val persist: suspend (MemberProfileDraft) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberProfileState())
    val state: StateFlow<MemberProfileState> = _state.asStateFlow()

    init {
        _state.update { it.copy(draft = readDraft()) }
    }

    fun onIntent(intent: MemberProfileIntent) {
        val next = reduceMemberProfile(_state.value, intent)
        _state.value = next
        if (next.saving) {
            viewModelScope.launch {
                val ok = try {
                    persist(next.draft)
                    true
                } catch (e: Exception) {
                    false
                }
                _state.update { current ->
                    reduceMemberProfile(current, MemberProfileIntent.SaveResult(ok))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                val reader = app.container.profileReader
                MemberProfileViewModel(
                    readDraft = {
                        MemberProfileDraft(
                            nickname = reader.nickname(),
                            birthDate = formatBirthDate(reader.birthYear(), reader.birthMonth(), reader.birthDay()),
                            gender = MemberGender.ofMale(reader.male()),
                            heightCm = reader.heightCm().takeIf { h -> h > 0 }?.toString() ?: "",
                            weightTargetKg = "",
                        )
                    },
                    persist = { draft ->
                        val birth = parseBirthDate(draft.birthDate)
                            ?: error("出生日期非法：${draft.birthDate}")
                        val height = draft.heightCm.trim().toIntOrNull()
                            ?: error("身高非法：${draft.heightCm}")
                        app.container.updateProfileService.execute(
                            UpdateProfileCommand(
                                // 「不告知」没有存储位：沿用档案现有性别，避免失真成固定值
                                male = if (draft.gender == MemberGender.Undisclosed) {
                                    reader.male()
                                } else {
                                    draft.gender == MemberGender.Male
                                },
                                birthYear = birth.first,
                                birthMonth = birth.second,
                                birthDay = birth.third,
                                heightCm = height,
                                nickname = draft.nickname,
                            ),
                        )
                    },
                )
            }
        }
    }
}
