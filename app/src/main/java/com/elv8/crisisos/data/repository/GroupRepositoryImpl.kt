package com.elv8.crisisos.data.repository

import android.graphics.Color
import com.elv8.crisisos.data.local.dao.GroupDao
import com.elv8.crisisos.data.local.entity.GroupEntity
import com.elv8.crisisos.domain.model.contact.Group
import com.elv8.crisisos.domain.model.contact.GroupType
import com.elv8.crisisos.domain.repository.GroupRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val identityRepository: IdentityRepository
) : GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFamilyGroup(): Group? = withContext(Dispatchers.IO) {
        groupDao.getGroupsByTypeSynchronous(GroupType.FAMILY.name).firstOrNull()?.toDomain()
    }

    override suspend fun getOrCreateFamilyGroup(): Group = withContext(Dispatchers.IO) {
        val existing = getFamilyGroup()
        if (existing != null) return@withContext existing

        val localId = identityRepository.getIdentity().firstOrNull()?.crsId ?: "unknown"
        val group = GroupEntity(
            groupId = UUID.randomUUID().toString(),
            name = "Family",
            description = "Your trusted family network",
            createdAt = System.currentTimeMillis(),
            createdByCrsId = localId,
            type = GroupType.FAMILY.name,
            memberCrsIds = emptyList(),
            avatarColor = Color.parseColor("#1D9E75")
        )
        groupDao.insertGroup(group)
        group.toDomain()
    }

    override suspend fun createGroup(
        name: String,
        description: String,
        type: GroupType
    ): Group = withContext(Dispatchers.IO) {
        val localId = identityRepository.getIdentity().firstOrNull()?.crsId ?: "unknown"
        val group = GroupEntity(
            groupId = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
            createdByCrsId = localId,
            type = type.name,
            memberCrsIds = emptyList(),
            avatarColor = Color.parseColor("#607D8B")
        )
        groupDao.insertGroup(group)
        group.toDomain()
    }

    override suspend fun addMember(groupId: String, crsId: String) = withContext(Dispatchers.IO) {
        val group = groupDao.getGroupById(groupId) ?: return@withContext
        val current = group.memberCrsIds.toMutableList()
        if (crsId in current) return@withContext
        current.add(crsId)
        
        groupDao.insertGroup(group.copy(memberCrsIds = current))
    }

    override suspend fun removeMember(groupId: String, crsId: String) = withContext(Dispatchers.IO) {
        val group = groupDao.getGroupById(groupId) ?: return@withContext
        val current = group.memberCrsIds.toMutableList()
        if (!current.remove(crsId)) return@withContext
        
        groupDao.insertGroup(group.copy(memberCrsIds = current))
    }

    override suspend fun deleteGroup(groupId: String) = withContext<Unit>(Dispatchers.IO) {
        groupDao.getGroupById(groupId)?.let {
            groupDao.deleteGroup(it)
        }
    }

    override suspend fun getGroup(groupId: String): Group? = withContext(Dispatchers.IO) {
        groupDao.getGroupById(groupId)?.toDomain()
    }

    private fun GroupEntity.toDomain() = Group(
        groupId = groupId,
        name = name,
        description = description,
        createdAt = createdAt,
        createdByCrsId = createdByCrsId,
        type = try { GroupType.valueOf(type) } catch (e: Exception) { GroupType.CUSTOM },
        memberCrsIds = memberCrsIds,
        avatarColor = avatarColor,
        memberCount = memberCrsIds.size
    )
}