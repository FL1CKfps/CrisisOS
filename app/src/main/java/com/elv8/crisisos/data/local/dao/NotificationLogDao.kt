package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.elv8.crisisos.data.local.entity.NotificationLogEntity

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(entry: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<NotificationLogEntity>>

    @Query("UPDATE notification_log SET wasDismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: Int): Int

    @Query("SELECT COUNT(*) FROM notification_log WHERE wasDismissed = 0 AND wasShown = 1")
    fun countUnread(): Flow<Int>

    @Query("DELETE FROM notification_log WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("SELECT * FROM notification_log WHERE eventType = :eventType")
    fun getByType(eventType: String): Flow<List<NotificationLogEntity>>
}
