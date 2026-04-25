package com.elv8.crisisos

import com.elv8.crisisos.data.local.db.CrisisDatabase
import com.elv8.crisisos.data.local.entity.*
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object MockDataSeeder {
    suspend fun seed(database: CrisisDatabase, localAlias: String = "Commander") {
        withContext(Dispatchers.IO) {
            // Check if identity already exists before fully seeding to avoid duplicates
            val existingId = database.userIdentityDao().getIdentityOnce()
            if (existingId != null) return@withContext

            val names = localAlias.split(" ")
            val firstName = names.getOrNull(0) ?: "Mock"
            val surname = names.getOrNull(1) ?: "User"
            val dob = "01012000"
            
            val localId = CrsIdGenerator.generate(firstName, surname, dob)

            database.userIdentityDao().insert(
                UserIdentityEntity(
                    crsId = localId,
                    deviceId = UUID.randomUUID().toString(),
                    alias = localAlias,
                    createdAt = System.currentTimeMillis(),
                    avatarColor = CrsIdGenerator.generateAvatarColor(localId),
                    publicKey = null
                )
            )
        }
    }
}
