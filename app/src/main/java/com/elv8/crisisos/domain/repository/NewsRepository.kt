package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.data.local.entity.NewsItemEntity
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun observe(): Flow<List<NewsItemEntity>>
    suspend fun publish(headline: String, body: String, category: String, isOfficial: Boolean)
    suspend fun purgeExpired()
    fun observeIncoming()
}
