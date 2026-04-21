package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Query("SELECT * FROM peers WHERE isNearby = 1 ORDER BY signalStrength DESC")
    fun getAllNearby(): Flow<List<PeerEntity>>
    // MUST return Flow<> not suspend List<> for UI reactivity

    @Query("SELECT * FROM peers ORDER BY lastSeenAt DESC")
    fun getAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE crsId = :crsId LIMIT 1")
    suspend fun getByCrsId(crsId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(peer: PeerEntity): Long
    // OnConflictStrategy.REPLACE is critical � ensures update triggers Flow

    @Query("UPDATE peers SET status = :status, lastSeenAt = :lastSeenAt WHERE crsId = :crsId")
    fun updateStatus(crsId: String, status: String, lastSeenAt: Long): Int

    @Query("UPDATE peers SET status = :status, lastSeen = :lastSeenAt WHERE crsId = :crsId")
    fun updateStatusLegacy(crsId: String, status: String, lastSeenAt: Long): Int

    @Query("UPDATE peers SET signalStrength = :signalStrength, distanceMeters = :distanceMeters WHERE crsId = :crsId")
    fun updateSignal(crsId: String, signalStrength: Int, distanceMeters: Float): Int

    @Query("UPDATE peers SET isNearby = 0, status = 'OFFLINE' WHERE isNearby = 1")
    fun markAllOffline(): Int

    @Query("DELETE FROM peers WHERE crsId = :crsId")
    fun delete(crsId: String): Int

    @Query("SELECT COUNT(*) FROM peers WHERE isNearby = 1")
    fun getNearbyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM peers WHERE isNearby = 1")
    suspend fun getNearbyCountOnce(): Int

    @Query("UPDATE peers SET lastSeenAt = :now, isNearby = 1 WHERE crsId = :crsId")
    fun touchLastSeen(crsId: String, now: Long): Int
}
