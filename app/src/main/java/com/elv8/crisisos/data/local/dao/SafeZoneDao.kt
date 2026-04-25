package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.SafeZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeZoneDao {
    @Query("SELECT * FROM safe_zones ORDER BY name ASC")
    fun getAll(): Flow<List<SafeZoneEntity>>

    @Query("SELECT COUNT(*) FROM safe_zones")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: SafeZoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<SafeZoneEntity>)

    @Query("UPDATE safe_zones SET currentOccupancy = :occupancy, isOperational = :operational, lastUpdated = :updated WHERE id = :id")
    suspend fun updateCapacity(id: String, occupancy: Int?, operational: Boolean, updated: Long): Int

    @Query("DELETE FROM safe_zones WHERE id = :id")
    suspend fun delete(id: String): Int
}
