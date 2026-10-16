package com.xcwk.scalehub.application.command

/**
 * command — 更新成员档案（由我的页 trigger 层组装，只带事实）。
 * nickname 传原始输入，服务层按旧库口径 trim().take(20) 归一。
 */
data class UpdateProfileCommand(
    val male: Boolean,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val heightCm: Int,
    val nickname: String,
)
