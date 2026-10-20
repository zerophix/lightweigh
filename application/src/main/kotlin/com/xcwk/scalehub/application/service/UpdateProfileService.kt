package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.UpdateProfileCommand
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileWriter

/**
 * application service — 更新成员档案用例（M4 我的切片）。
 * 语义：昵称按旧库 trim().take(20) 归一（ProfilePages.kt:491）后构造——
 * 任何越界输入在 MemberProfile 构造守卫处抛 DomainException（fail-fast），
 * writer 只接收完整合法档案。时间戳/版本戳不适用（本机档案非事件流）。
 *
 * 档案是体成分计算的输入，变更后报告页与我的页都需重算，故发 [DataChange.Profile]；
 * 刷 UI 与推云均由容器的事件订阅承担（DEC-034 任务组 A）。
 */
class UpdateProfileService(
    private val writer: MemberProfileWriter,
    private val refreshBus: DataRefreshBus? = null,
) {

    suspend fun execute(cmd: UpdateProfileCommand): MemberProfile {
        // 昵称归一与越界守卫都在 MemberProfile（DEC-034 组 C），本用例只编排落库
        val profile = MemberProfile.create(
            male = cmd.male,
            birthYear = cmd.birthYear,
            birthMonth = cmd.birthMonth,
            birthDay = cmd.birthDay,
            heightCm = cmd.heightCm,
            nickname = cmd.nickname,
        )
        writer.save(profile)
        refreshBus?.notifyChanged(DataChange.Profile)
        return profile
    }
}
