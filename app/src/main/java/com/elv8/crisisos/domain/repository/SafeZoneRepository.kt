package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.SafeZone
import kotlinx.coroutines.flow.Flow

interface SafeZoneRepository {
    fun observe(): Flow<List<SafeZone>>
    suspend fun seedDefaultsIfEmpty(centerLat: Double, centerLon: Double)
    suspend fun upsert(zone: SafeZone)
    suspend fun updateOccupancy(id: String, occupancy: Int?, operational: Boolean)
    suspend fun delete(id: String)
}
