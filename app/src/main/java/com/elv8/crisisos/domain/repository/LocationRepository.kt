package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.location.CrisisLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getCurrentLocation(): Flow<CrisisLocation?>
    suspend fun getLastKnownLocation(): CrisisLocation?
    fun startTracking()
    fun stopTracking()
}
