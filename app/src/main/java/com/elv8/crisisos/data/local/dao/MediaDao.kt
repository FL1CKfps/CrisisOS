package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elv8.crisisos.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMediaForThread(threadId: String): Flow<List<MediaEntity>>

    @Query("""
        SELECT * FROM media_items 
        WHERE threadId = :threadId 
        AND type IN ('IMAGE', 'VIDEO')
        ORDER BY timestamp DESC
    """)
    fun getSharedMediaForThread(threadId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE mediaId = :mediaId")
    suspend fun getById(mediaId: String): MediaEntity?

    @Query("SELECT * FROM media_items WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity): Int

    @Query("UPDATE media_items SET status = :status WHERE mediaId = :mediaId")
    suspend fun updateStatus(mediaId: String, status: String): Int

    @Query("UPDATE media_items SET localUri = :uri, status = :status WHERE mediaId = :mediaId")
    suspend fun updateLocalUri(mediaId: String, uri: String, status: String): Int

    @Query("UPDATE media_items SET thumbnailUri = :uri WHERE mediaId = :mediaId")
    suspend fun updateThumbnailUri(mediaId: String, uri: String): Int

    @Query("""
        UPDATE media_items 
        SET chunksReceived = :chunksReceived, status = :status 
        WHERE mediaId = :mediaId
    """)
    suspend fun updateChunkProgress(mediaId: String, chunksReceived: Int, status: String): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE threadId = :threadId AND type IN ('IMAGE', 'VIDEO')")
    fun getSharedMediaCount(threadId: String): Flow<Int>

    @Query("""
        SELECT * FROM media_items 
        WHERE status IN ('PENDING', 'SENDING') 
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingSends(): List<MediaEntity>

    @Query("DELETE FROM media_items WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String): Int

    @Query("""
        DELETE FROM media_items 
        WHERE localUri IS NULL 
        AND status = 'EXPIRED' 
        AND timestamp < :cutoff
    """)
    suspend fun deleteExpired(cutoff: Long): Int
}
