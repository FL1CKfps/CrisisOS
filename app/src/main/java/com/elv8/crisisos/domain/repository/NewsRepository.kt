package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.data.local.entity.NewsItemEntity
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun observe(): Flow<List<NewsItemEntity>>
    /**
     * Publish a CrisisNews item. The `isOfficial` flag is derived at the
     * repository boundary from the authenticated identity (NGO alias check) —
     * callers cannot self-elevate by passing a flag.
     */
    suspend fun publish(headline: String, body: String, category: String)
    suspend fun purgeExpired()
    fun observeIncoming()
}
