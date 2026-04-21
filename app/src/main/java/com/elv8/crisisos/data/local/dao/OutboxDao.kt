package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.OutboxMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox_messages WHERE status = 'PENDING' AND ttlExpiry > :now AND attemptCount < maxAttempts ORDER BY priority DESC, createdAt ASC")
    fun getPendingMessages(now: Long): Flow<List<OutboxMessageEntity>>

    @Query("SELECT * FROM outbox_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): OutboxMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutboxMessageEntity): Long

    @Query("UPDATE outbox_messages SET status = :status, lastAttemptAt = :lastAttemptAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, lastAttemptAt: Long): Int

    @Query("UPDATE outbox_messages SET attemptCount = attemptCount + 1, lastAttemptAt = :lastAttemptAt WHERE id = :id")
    suspend fun incrementAttempt(id: String, lastAttemptAt: Long): Int

    @Query("UPDATE outbox_messages SET status = 'SENT' WHERE id = :id")
    suspend fun markSent(id: String): Int

    @Query("UPDATE outbox_messages SET status = 'FAILED', failureReason = :reason WHERE id = :id")
    suspend fun markFailed(id: String, reason: String): Int

    @Query("DELETE FROM outbox_messages WHERE ttlExpiry < :now OR status = 'SENT'")
    suspend fun deleteExpired(now: Long): Int

    @Query("UPDATE outbox_messages SET status = 'EXPIRED' WHERE status = 'PENDING' AND attemptCount >= maxAttempts")
    suspend fun markExhausted(): Int

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = :status")       
    suspend fun countByStatus(status: String): Int

    @Query("SELECT * FROM outbox_messages WHERE status = :status")
    fun getByStatus(status: String): Flow<List<OutboxMessageEntity>>

    @Query("UPDATE outbox_messages SET status = 'PENDING', failureReason = null WHERE status = 'FAILED' AND attemptCount < maxAttempts")
    suspend fun resetFailedToPending(): Int
    @Query("SELECT * FROM outbox_messages ORDER BY createdAt DESC")
    fun getAllMessages(): Flow<List<OutboxMessageEntity>>
}
