package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityPostDao {
    @Query("SELECT * FROM community_posts WHERE expiresAt > :now ORDER BY pinned DESC, createdAt DESC")
    fun getAllActive(now: Long = System.currentTimeMillis()): Flow<List<CommunityPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: CommunityPostEntity)

    @Query("UPDATE community_posts SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean): Int

    @Query("DELETE FROM community_posts WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM community_posts WHERE id = :id")
    suspend fun exists(id: String): Int
}
