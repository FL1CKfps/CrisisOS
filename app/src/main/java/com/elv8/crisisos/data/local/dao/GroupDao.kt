package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE type = :type")
    fun getGroupsByType(type: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE type = :type")
    fun getGroupsByTypeSynchronous(type: String): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun getGroupById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: GroupEntity): Long

    @Delete
    fun deleteGroup(group: GroupEntity): Int
}
