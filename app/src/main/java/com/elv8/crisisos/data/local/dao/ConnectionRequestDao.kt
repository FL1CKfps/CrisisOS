package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.ConnectionRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionRequestDao {
    @Query("SELECT * FROM connection_requests WHERE direction = 'INCOMING' AND status = 'PENDING' ORDER BY sentAt DESC")
    fun getIncoming(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT * FROM connection_requests WHERE direction = 'OUTGOING' ORDER BY sentAt DESC")
    fun getOutgoing(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT COUNT(*) FROM connection_requests WHERE status = 'PENDING' AND direction = 'INCOMING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM connection_requests WHERE requestId = :requestId LIMIT 1")
    fun getById(requestId: String): ConnectionRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(request: ConnectionRequestEntity): Long

    @Query("UPDATE connection_requests SET status = :status, respondedAt = :respondedAt WHERE requestId = :requestId")
    fun updateStatus(requestId: String, status: String, respondedAt: Long): Int

    @Query("UPDATE connection_requests SET status = 'EXPIRED' WHERE expiresAt < :now AND status = 'PENDING'")
    fun expireOld(now: Long): Int

    @Query("SELECT COUNT(*) FROM connection_requests WHERE fromCrsId = :fromCrsId AND toCrsId = :toCrsId AND status IN ('PENDING', 'ACCEPTED')")
    fun existsBetween(fromCrsId: String, toCrsId: String): Int
}
