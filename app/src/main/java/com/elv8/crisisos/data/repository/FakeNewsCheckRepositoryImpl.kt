package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.FakeNewsCheckDao
import com.elv8.crisisos.data.local.entity.FakeNewsCheckEntity
import com.elv8.crisisos.domain.repository.FakeNewsCheckRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNewsCheckRepositoryImpl @Inject constructor(
    private val dao: FakeNewsCheckDao
) : FakeNewsCheckRepository {

    override fun observeRecent(limit: Int) = dao.getRecent(limit)

    override suspend fun record(check: FakeNewsCheckEntity) = dao.insert(check)
}
