package com.xcwk.scalehub.application

import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import com.xcwk.scalehub.domain.model.MemberProfile

/**
 * application service — 成员档案编排。
 *
 * 职责：
 *  - 校验并保存修改后的档案（MemberProfile 守卫）
 *  - 区分「空态」（未填写任何有效值） → UI 提示引导填写
 *  - 保存失败 → 返回失败原因文案（吞异常，不抛给 UI）
 *  - 仅正式用户才能保存（游客模式只读）
 *
 * 旧库 UpdateProfileService 语义移植（UpdateProfileStore:159-181）。
 */
class MemberProfileService(
    private val reader: MemberProfileReader,
    private val writer: MemberProfileWriter,
) {

    /**
     * 保存档案修改。
     * @return 保存结果，ok=false 时 message 解释失败原因
     */
    suspend fun save(
        male: Boolean,
        birthYear: Int,
        birthMonth: Int,
        birthDay: Int,
        heightCm: Int,
        nickname: String,
    ): MemberProfileResult {
        // 空态判定：身高和生日都为空 → 提示填写
        if (heightCm <= 0 && birthYear <= 0) {
            return MemberProfileResult(false, "请填写身高和生日", ProfileState.Empty)
        }
        // 仅身高填写但年龄缺失 → 引导
        if (heightCm <= 0) {
            return MemberProfileResult(false, "请填写身高", ProfileState.Dirty)
        }
        if (birthYear <= 0) {
            return MemberProfileResult(false, "请填写生日", ProfileState.Dirty)
        }
        // 守卫：身高越界
        if (heightCm < 50 || heightCm > 300) {
            return MemberProfileResult(false, "身高超出合理范围（50-300cm）", ProfileState.Dirty)
        }
        val profile = runCatching {
            MemberProfile(
                male = male,
                birthYear = birthYear,
                birthMonth = birthMonth,
                birthDay = birthDay,
                heightCm = heightCm,
                nickname = nickname,
            )
        }.onFailure {
            return MemberProfileResult(false, it.message ?: "档案信息不完整", ProfileState.Dirty)
        }
        return profile.getOrNull()?.let { p ->
            runCatching {
                writer.save(p)
                MemberProfileResult(true, "保存成功", ProfileState.Complete)
            }.onFailure {
                MemberProfileResult(false, "保存失败：${it.message ?: "未知错误"}", ProfileState.Dirty)
            }.getOrDefault(MemberProfileResult(false, "保存失败", ProfileState.Dirty))
        } ?: MemberProfileResult(false, "档案校验失败", ProfileState.Dirty)
    }

    /** 当前档案是否为空态（游客、未填写）。 */
    suspend fun isEmpty(): Boolean {
        val height = reader.heightCm()
        val birthYear = reader.birthYear()
        return height <= 0 && birthYear <= 0
    }

    /** 当前档案摘要（用于 UI 回显）。 */
    suspend fun current(): ProfileSummary {
        return ProfileSummary(
            male = reader.male(),
            birthYear = reader.birthYear(),
            birthMonth = reader.birthMonth(),
            birthDay = reader.birthDay(),
            heightCm = reader.heightCm(),
            nickname = reader.nickname(),
        )
    }
}

/** 保存结果。 */
data class MemberProfileResult(
    val ok: Boolean,
    val message: String,
    val state: ProfileState,
)

/** 档案状态。 */
enum class ProfileState {
    Empty,     // 未填写任何有效值
    Dirty,     // 填写了部分但未通过校验
    Complete,  // 保存成功
}

/** 档案摘要。 */
data class ProfileSummary(
    val male: Boolean,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val heightCm: Int,
    val nickname: String,
)
