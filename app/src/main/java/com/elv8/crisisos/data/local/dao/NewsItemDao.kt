package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import kotlinx.coroutines.flow.Flow

// NOTE: no default arguments on abstract members — KSP2 (Analysis API) on
// Kotlin 2.2.10 + Room crashes with "unexpected jvm signature V" when an
// @Dao interface declares default-valued parameters on abstract methods.
// Callers must pass `System.currentTimeMillis()` explicitly.
@Dao
interface NewsItemDao {
    @Query("SELECT * FROM news_items WHERE expiresAt > :now ORDER BY publishedAt DESC")
    fun getAllActive(now: Long): Flow<List<NewsItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NewsItemEntity)

    @Query("DELETE FROM news_items WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM news_items WHERE id = :id")
    suspend fun exists(id: String): Int
}
