package com.elv8.crisisos.domain.model.profile

import com.elv8.crisisos.domain.model.contact.TrustLevel

data class PeerProfile(
    val crsId: String,
    val alias: String,
    val avatarColor: Int,
    val trustLevel: TrustLevel?,
    val isContact: Boolean,
    val isBlocked: Boolean,
    val addedAt: Long?,
    val isSelf: Boolean
)
