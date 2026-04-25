package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import kotlinx.coroutines.flow.Flow

interface CommunityBoardRepository {
    fun observe(): Flow<List<CommunityPostEntity>>
    suspend fun post(body: String, category: String, pinned: Boolean = false)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun purgeExpired()
    fun observeIncoming()
}
