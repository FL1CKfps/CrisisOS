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

    /**
     * Pull live conflict / humanitarian news from external authoritative
     * sources (ACLED for the user's country, GDELT for credible global news)
     * and persist any new items into the local Room store. Existing items are
     * not duplicated. Safe to call on any thread; returns the number of new
     * items ingested. Errors are swallowed and reported as 0.
     */
    suspend fun refreshFromOnlineSources(): Int
}
