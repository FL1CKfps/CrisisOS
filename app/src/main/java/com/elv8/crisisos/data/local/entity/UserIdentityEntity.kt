package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elv8.crisisos.domain.model.identity.UserIdentity

@Entity(tableName = "user_identity")
data class UserIdentityEntity(
    @PrimaryKey
    val crsId: String,
    val alias: String,
    val deviceId: String,
    val createdAt: Long,
    val avatarColor: Int,
    val publicKey: String?
) {
    fun toDomain(): UserIdentity = UserIdentity(
        crsId = crsId,
        alias = alias,
        deviceId = deviceId,
        createdAt = createdAt,
        avatarColor = avatarColor,
        publicKey = publicKey
    )
}
