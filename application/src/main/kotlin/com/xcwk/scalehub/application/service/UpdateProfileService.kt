package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.command.UpdateProfileCommand
import com.xcwk.scalehub.application.sync.SyncTrigger
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileWriter

/**
 * application service — 更新成员档案用例（M4 我的切片）。
 * 语义：昵称按旧库 trim().take(20) 归一（ProfilePages.kt:491）后构造——
 * 任何越界输入在 MemberProfile 构造守卫处抛 DomainException（fail-fast），
 * writer 只接收完整合法档案。时间戳/版本戳不适用（本机档案非事件流）。
 * 落库后点火云推送（P0 断链修复：档案变更随 profile 行上云，失败不阻塞用例）。
 */
class UpdateProfileService(
    private val writer: MemberProfileWriter,
    private val syncTrigger: SyncTrigger? = null,
) {

    suspend fun execute(cmd: UpdateProfileCommand): MemberProfile {
        val profile = MemberProfile(
            male = cmd.male,
            birthYear = cmd.birthYear,
            birthMonth = cmd.birthMonth,
            birthDay = cmd.birthDay,
            heightCm = cmd.heightCm,
            nickname = cmd.nickname.trim().take(20),
        )
        writer.save(profile)
        syncTrigger?.fire()
        return profile
    }
}
