package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.FakeNewsCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FakeNewsCheckDao {
    @Query("SELECT * FROM fake_news_checks ORDER BY checkedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<FakeNewsCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: FakeNewsCheckEntity)

    @Query("DELETE FROM fake_news_checks WHERE checkedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
