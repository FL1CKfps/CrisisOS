package com.elv8.crisisos.domain.model.identity

data class UserIdentity(
    val crsId: String,
    val alias: String,
    val deviceId: String,
    val createdAt: Long,
    val avatarColor: Int,
    val publicKey: String?
)
