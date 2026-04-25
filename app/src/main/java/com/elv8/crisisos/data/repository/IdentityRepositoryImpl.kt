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

    override suspend fun getOrCreateIdentity(
        deviceId: String,
        firstName: String,
        surname: String,
        dob: String
    ): UserIdentity {
        val existing = userIdentityDao.getIdentityOnce()
        if (existing != null) {
            return existing.toDomain()
        }

        val newCrsId = CrsIdGenerator.generate(firstName, surname, dob)
        val alias = "$firstName $surname"
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
        // Defense-in-depth for the NGO authority gate (CrisisNews / Community Board):
        // CrisisNews "official" and Community "pinned" privileges are derived from
        // an "NGO_*" / "*_OFFICIAL" alias heuristic. Without cryptographic packet
        // signing (a known followup tracked in replit.md), the only way to keep
        // that gate meaningful is to refuse self-service NGO alias adoption from
        // the in-app rename flow. Real NGO provisioning will arrive via a signed
        // onboarding bundle that bypasses this guard.
        val sanitized = newAlias.trim()
        require(sanitized.isNotEmpty()) { "Alias cannot be blank" }
        val looksLikeNgo = sanitized.startsWith("NGO_") || sanitized.endsWith("_OFFICIAL")
        if (looksLikeNgo) {
            throw SecurityException(
                "NGO aliases (NGO_* / *_OFFICIAL) are reserved for verified " +
                "organisations and cannot be self-assigned."
            )
        }
        val current = userIdentityDao.getIdentityOnce()
        if (current != null) {
            userIdentityDao.updateAlias(current.crsId, sanitized)
        }
    }

    override fun isIdentitySetup(): Boolean {
        return userIdentityDao.hasIdentitySync()
    }
}
