package com.qingke.health.domain.model

/** 本机测量用户。体脂秤在用户之间共用。 */
data class Member(val id: String, val slot: Int, val nickname: String)
