package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.local.dao.SafeZoneDao
import com.elv8.crisisos.data.local.entity.SafeZoneEntity
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.repository.SafeZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafeZoneRepositoryImpl @Inject constructor(
    private val dao: SafeZoneDao,
    private val eventBus: EventBus
) : SafeZoneRepository {

    override fun observe(): Flow<List<SafeZone>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun seedDefaultsIfEmpty(centerLat: Double, centerLon: Double) {
        if (dao.count() > 0) return
        // First-run regional shard seed. These are the same six anchor sites shipped with
        // the install — once NGOs report real capacity over the mesh, those rows replace these.
        val now = System.currentTimeMillis()
        val seed = listOf(
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "Central Stadium Camp",
                type = SafeZoneType.CAMP.name,
                latitude = centerLat + 0.0090,
                longitude = centerLon + 0.0010,
                capacity = 2500,
                currentOccupancy = 2100,
                isOperational = true,
                operatedBy = "UNHCR",
                lastUpdated = now
            ),
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "City West Hospital",
                type = SafeZoneType.HOSPITAL.name,
                latitude = centerLat + 0.0020,
                longitude = centerLon - 0.0340,
                capacity = 500,
                currentOccupancy = 480,
                isOperational = true,
                operatedBy = "MSF",
                lastUpdated = now
            ),
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "Plaza Water Dispenser",
                type = SafeZoneType.WATER_POINT.name,
                latitude = centerLat - 0.0036,
                longitude = centerLon + 0.0008,
                capacity = null,
                currentOccupancy = null,
                isOperational = true,
                operatedBy = "Local Relief Org",
                lastUpdated = now
            ),
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "North Sector Distribution",
                type = SafeZoneType.FOOD_DISTRIBUTION.name,
                latitude = centerLat + 0.0250,
                longitude = centerLon - 0.0010,
                capacity = 1000,
                currentOccupancy = 1000,
                isOperational = false,
                operatedBy = "World Central Kitchen",
                lastUpdated = now
            ),
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "Embassy Extraction Zone",
                type = SafeZoneType.EVACUATION_POINT.name,
                latitude = centerLat + 0.0040,
                longitude = centerLon + 0.0570,
                capacity = 5000,
                currentOccupancy = 1200,
                isOperational = true,
                operatedBy = "Joint Task Force",
                lastUpdated = now
            ),
            SafeZoneEntity(
                id = UUID.randomUUID().toString(),
                name = "Old Quarter Safe House",
                type = SafeZoneType.SAFE_HOUSE.name,
                latitude = centerLat - 0.0080,
                longitude = centerLon - 0.0050,
                capacity = 40,
                currentOccupancy = 12,
                isOperational = true,
                operatedBy = "Civilian Network",
                lastUpdated = now
            )
        )
        dao.insertAll(seed)
    }

    override suspend fun upsert(zone: SafeZone) {
        dao.insert(SafeZoneEntity.fromDomain(zone))
    }

    override suspend fun updateOccupancy(id: String, occupancy: Int?, operational: Boolean) {
        // Spec (Feature 2 § "Camp capacity"): when a CAMP **crosses** 95%
        // occupancy, broadcast a hint so nearby camps can absorb the
        // overflow ("Camp A near capacity, redirect incoming"). We compare
        // before/after ratios so the event only fires on the rising edge —
        // not on every subsequent NGO update at >=95%.
        val before = dao.getById(id)
        val beforeRatio = before?.let { ratioOf(it.capacity, it.currentOccupancy, it.isOperational) }

        dao.updateCapacity(id, occupancy, operational, System.currentTimeMillis())

        val updated = dao.getById(id) ?: return
        if (updated.type != SafeZoneType.CAMP.name) return
        val newRatio = ratioOf(updated.capacity, updated.currentOccupancy, updated.isOperational) ?: return

        val crossedRising = (beforeRatio == null || beforeRatio < NEAR_CAPACITY_THRESHOLD) &&
            newRatio >= NEAR_CAPACITY_THRESHOLD
        if (crossedRising) {
            eventBus.tryEmit(
                AppEvent.CapacityEvent.CampNearCapacity(
                    zoneId = updated.id,
                    zoneName = updated.name,
                    occupancyRatio = newRatio,
                    latitude = updated.latitude,
                    longitude = updated.longitude
                )
            )
        }
    }

    private fun ratioOf(cap: Int?, occ: Int?, operational: Boolean): Float? {
        if (!operational) return null
        if (cap == null || occ == null || cap <= 0) return null
        return occ.toFloat() / cap.toFloat()
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    companion object {
        // 95% — the threshold from CrisisOS_Context.md Feature 2.
        const val NEAR_CAPACITY_THRESHOLD = 0.95f
    }
}
