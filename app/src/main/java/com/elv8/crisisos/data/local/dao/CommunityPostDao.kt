package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import kotlinx.coroutines.flow.Flow

// NOTE: no default arguments on abstract members — KSP2 (Analysis API) on
// Kotlin 2.2.10 + Room crashes with "unexpected jvm signature V" when an
// @Dao interface declares default-valued parameters on abstract methods.
// Callers must pass `System.currentTimeMillis()` explicitly.
@Dao
interface CommunityPostDao {
    @Query("SELECT * FROM community_posts WHERE expiresAt > :now ORDER BY pinned DESC, createdAt DESC")
    fun getAllActive(now: Long): Flow<List<CommunityPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: CommunityPostEntity)

    @Query("UPDATE community_posts SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean): Int

    @Query("DELETE FROM community_posts WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM community_posts WHERE id = :id")
    suspend fun exists(id: String): Int
}
