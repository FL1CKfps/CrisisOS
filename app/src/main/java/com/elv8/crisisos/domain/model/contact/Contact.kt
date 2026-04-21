package com.elv8.crisisos.domain.model.contact

data class Contact(
    val crsId: String,
    val alias: String,
    val addedAt: Long,
    val groupId: String?,
    val trustLevel: TrustLevel,
    val notes: String,
    val avatarColor: Int,
    val isFavorite: Boolean,
    val isBlocked: Boolean
)