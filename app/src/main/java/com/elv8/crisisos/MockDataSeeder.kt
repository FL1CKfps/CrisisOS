package com.elv8.crisisos

import android.content.Context
import com.elv8.crisisos.data.local.db.CrisisDatabase
import com.elv8.crisisos.data.local.entity.*
import com.elv8.crisisos.domain.model.identity.CrsIdGenerator
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object MockDataSeeder {
    suspend fun seed(database: CrisisDatabase, localAlias: String = "Commander") {
        withContext(Dispatchers.IO) {
            // Check if identity already exists before fully seeding to avoid duplicates
            val existingId = database.userIdentityDao().getIdentityOnce()
            if (existingId != null) return@withContext

            val localId = CrsIdGenerator.generate()
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
