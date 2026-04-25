package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.DeconflictionReport
import kotlinx.coroutines.flow.Flow

interface DeconflictionRepository {
    fun observe(): Flow<List<DeconflictionReport>>
    suspend fun submit(report: DeconflictionReport, submittedBy: String)
    fun observeIncoming()
}
