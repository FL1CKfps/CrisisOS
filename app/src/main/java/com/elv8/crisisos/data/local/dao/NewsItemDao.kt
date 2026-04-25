package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsItemDao {
    @Query("SELECT * FROM news_items WHERE expiresAt > :now ORDER BY publishedAt DESC")
    fun getAllActive(now: Long = System.currentTimeMillis()): Flow<List<NewsItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NewsItemEntity)

    @Query("DELETE FROM news_items WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM news_items WHERE id = :id")
    suspend fun exists(id: String): Int
}
