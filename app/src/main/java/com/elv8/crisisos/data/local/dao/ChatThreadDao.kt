package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads ORDER BY isPinned DESC, lastMessageAt DESC")
    fun getAllThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE peerCrsId = :peerCrsId LIMIT 1")
    fun getDirectThread(peerCrsId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE groupId = :groupId LIMIT 1")
    fun getGroupThread(groupId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE threadId = :threadId LIMIT 1")
    fun getById(threadId: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ChatThreadEntity): Long

    @Update
    suspend fun update(thread: ChatThreadEntity): Int

    @Query("UPDATE chat_threads SET lastMessagePreview = :preview, lastMessageAt = :lastMessageAt WHERE threadId = :threadId")
    suspend fun updateLastMessage(threadId: String, preview: String, lastMessageAt: Long): Int  

    @Query("UPDATE chat_threads SET unreadCount = unreadCount + 1 WHERE threadId = :threadId")
    suspend fun incrementUnread(threadId: String): Int

    @Query("UPDATE chat_threads SET unreadCount = 0 WHERE threadId = :threadId")   
    suspend fun markRead(threadId: String): Int

    @Query("DELETE FROM chat_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: String): Int
}
