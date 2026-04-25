package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.data.local.entity.FakeNewsCheckEntity
import kotlinx.coroutines.flow.Flow

interface FakeNewsCheckRepository {
    fun observeRecent(limit: Int = 30): Flow<List<FakeNewsCheckEntity>>
    suspend fun record(check: FakeNewsCheckEntity)
}
