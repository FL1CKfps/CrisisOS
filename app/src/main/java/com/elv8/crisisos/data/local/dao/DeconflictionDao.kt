package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.DeconflictionReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeconflictionDao {
    @Query("SELECT * FROM deconfliction_reports ORDER BY submittedAt DESC")
    fun getAll(): Flow<List<DeconflictionReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: DeconflictionReportEntity)

    @Query("SELECT * FROM deconfliction_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DeconflictionReportEntity?
}
