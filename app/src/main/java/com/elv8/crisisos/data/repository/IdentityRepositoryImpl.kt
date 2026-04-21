package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.UserIdentityDao
import com.elv8.crisisos.data.local.entity.UserIdentityEntity
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import com.elv8.crisisos.domain.model.identity.UserIdentity
import com.elv8.crisisos.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepositoryImpl @Inject constructor(
    private val userIdentityDao: UserIdentityDao
) : IdentityRepository {

    override suspend fun getOrCreateIdentity(deviceId: String, alias: String): UserIdentity {
        val existing = userIdentityDao.getIdentityOnce()
        if (existing != null) {
            return existing.toDomain()
        }

        val newCrsId = CrsIdGenerator.generate()
        val newEntity = UserIdentityEntity(
            crsId = newCrsId,
            alias = alias,
            deviceId = deviceId,
            createdAt = System.currentTimeMillis(),
            avatarColor = CrsIdGenerator.generateAvatarColor(newCrsId),
            publicKey = null
        )
        userIdentityDao.insert(newEntity)
        return newEntity.toDomain()
    }

    override fun getIdentity(): Flow<UserIdentity?> {
        return userIdentityDao.getIdentity().map { it?.toDomain() }
    }

    override suspend fun updateAlias(newAlias: String) {
        val current = userIdentityDao.getIdentityOnce()
        if (current != null) {
            userIdentityDao.updateAlias(current.crsId, newAlias)
        }
    }

    override fun isIdentitySetup(): Boolean {
        return userIdentityDao.hasIdentitySync()
    }
}
