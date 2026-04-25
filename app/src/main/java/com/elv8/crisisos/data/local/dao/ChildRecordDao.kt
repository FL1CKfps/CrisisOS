package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.ChildRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildRecordDao {
    @Query("SELECT * FROM child_records ORDER BY registeredAt DESC")
    fun getAll(): Flow<List<ChildRecordEntity>>

    @Query("SELECT * FROM child_records WHERE registeredBy = :guardianCrsId ORDER BY registeredAt DESC")
    fun getByGuardian(guardianCrsId: String): Flow<List<ChildRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ChildRecordEntity)

    @Query("UPDATE child_records SET status = :status, locatedAt = :locatedAt WHERE crsChildId = :crsChildId")
    suspend fun updateStatus(crsChildId: String, status: String, locatedAt: String?): Int

    @Query("DELETE FROM child_records WHERE crsChildId = :crsChildId")
    suspend fun delete(crsChildId: String): Int
}
