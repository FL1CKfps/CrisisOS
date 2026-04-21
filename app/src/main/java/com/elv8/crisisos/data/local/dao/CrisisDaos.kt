package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

import com.elv8.crisisos.data.local.entity.ChatMessageEntity
import com.elv8.crisisos.data.local.entity.SupplyRequestEntity
import com.elv8.crisisos.data.local.entity.MissingPersonEntity
import com.elv8.crisisos.data.local.entity.DangerZoneEntity

@Dao
interface MeshDao

@Dao
interface SosDao

@Dao
interface MissingPersonDao {
    @Query("SELECT * FROM missing_persons")
    fun getAllMissingPersons(): Flow<List<MissingPersonEntity>>

    @Query("SELECT * FROM missing_persons WHERE name LIKE '%' || :query || '%'")
    fun searchPersons(query: String): Flow<List<MissingPersonEntity>>

    @Query("SELECT * FROM missing_persons WHERE name LIKE '%' || :query || '%'")
    suspend fun searchPersonsDirect(query: String): List<MissingPersonEntity>   

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPerson(person: MissingPersonEntity): Long
}

@Dao
interface SupplyDao {
    @Query("SELECT * FROM supply_requests ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<SupplyRequestEntity>>

    @Query("SELECT * FROM supply_requests WHERE packetId = :packetId LIMIT 1")  
    suspend fun getRequestByPacketId(packetId: String): SupplyRequestEntity?    
    @Query("SELECT * FROM supply_requests WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: String): SupplyRequestEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: SupplyRequestEntity): Long
}

@Dao
interface DeadManDao

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoints ORDER BY lastUpdated DESC")
    fun getAll(): Flow<List<com.elv8.crisisos.data.local.entity.CheckpointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: com.elv8.crisisos.data.local.entity.CheckpointEntity): Long

    @Query("UPDATE checkpoints SET safetyRating = :safetyRating, isOpen = :isOpen, notes = :notes, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateRating(id: String, safetyRating: Int, isOpen: Boolean, notes: String, lastUpdated: Long): Int

    @Query("UPDATE checkpoints SET reportCount = reportCount + 1 WHERE id = :id")
    suspend fun incrementReportCount(id: String): Int

    @Query("DELETE FROM checkpoints WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}

@Dao
interface DangerZoneDao {
    @Query("SELECT * FROM danger_zones ORDER BY timestamp DESC")
    fun getAllDangerZones(): Flow<List<DangerZoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDangerZone(zone: DangerZoneEntity): Long

    @Query("DELETE FROM danger_zones WHERE timestamp < :olderThanMillis")       
    suspend fun deleteOlderThan(olderThanMillis: Long): Int
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :id")  
    suspend fun markAsDelivered(id: String, status: com.elv8.crisisos.domain.model.MessageStatus): Int
    
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT * FROM chat_messages 
        WHERE threadId = :threadId 
        AND (messageType = 'IMAGE' OR messageType = 'VIDEO')
        ORDER BY timestamp DESC
    """)
    fun getMediaMessagesForThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("""
        UPDATE chat_messages 
        SET mediaId = :mediaId, mediaThumbnailUri = :thumbnailUri, deliveryStatus = :status
        WHERE id = :messageId
    """)
    suspend fun linkMedia(messageId: String, mediaId: String, thumbnailUri: String?, status: String): Int

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMessage(threadId: String): ChatMessageEntity?

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId AND isOwn = 0 AND deliveryStatus != 'READ'")
    fun getUnreadCount(threadId: String): Flow<Int>
}

