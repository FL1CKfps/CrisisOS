package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.contact.Group
import com.elv8.crisisos.domain.model.contact.GroupType
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAllGroups(): Flow<List<Group>>
    suspend fun getFamilyGroup(): Group?
    suspend fun getOrCreateFamilyGroup(): Group
    suspend fun createGroup(name: String, description: String, type: GroupType): Group
    suspend fun addMember(groupId: String, crsId: String)
    suspend fun removeMember(groupId: String, crsId: String)
    suspend fun deleteGroup(groupId: String)
    suspend fun getGroup(groupId: String): Group?
}