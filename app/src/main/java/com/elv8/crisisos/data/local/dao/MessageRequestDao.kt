package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.MessageRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageRequestDao {
    @Query("SELECT * FROM message_requests WHERE status = 'PENDING' ORDER BY sentAt DESC")
    fun getPending(): Flow<List<MessageRequestEntity>>

    @Query("SELECT COUNT(*) FROM message_requests WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM message_requests WHERE requestId = :requestId LIMIT 1")
    fun getById(requestId: String): MessageRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(request: MessageRequestEntity): Long

    @Query("UPDATE message_requests SET status = :status, threadId = :threadId WHERE requestId = :requestId")
    fun updateStatus(requestId: String, status: String, threadId: String?): Int

    @Query("DELETE FROM message_requests WHERE sentAt < :cutoff AND status != 'ACCEPTED'")
    fun deleteExpired(cutoff: Long): Int
}
