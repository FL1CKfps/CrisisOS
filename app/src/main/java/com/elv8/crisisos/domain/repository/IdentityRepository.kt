package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.identity.UserIdentity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    suspend fun getOrCreateIdentity(
        deviceId: String,
        firstName: String,
        surname: String,
        dob: String
    ): UserIdentity
    fun getIdentity(): Flow<UserIdentity?>
    suspend fun updateAlias(newAlias: String)
    fun isIdentitySetup(): Boolean
}
