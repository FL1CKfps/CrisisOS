package com.elv8.crisisos.domain.model.contact

data class Group(
    val groupId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val createdByCrsId: String,
    val type: GroupType,
    val memberCrsIds: List<String>,
    val avatarColor: Int,
    val memberCount: Int
)