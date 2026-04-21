package com.elv8.crisisos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elv8.crisisos.data.local.entity.UserIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserIdentityDao {
    @Query("SELECT * FROM user_identity LIMIT 1")
    fun getIdentity(): Flow<UserIdentityEntity?>

    @Query("SELECT * FROM user_identity LIMIT 1")
    suspend fun getIdentityOnce(): UserIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: UserIdentityEntity): Long

    @Query("UPDATE user_identity SET alias = :alias WHERE crsId = :crsId")
    suspend fun updateAlias(crsId: String, alias: String): Int

    @Query("UPDATE user_identity SET publicKey = :publicKey WHERE crsId = :crsId")
    suspend fun updatePublicKey(crsId: String, publicKey: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM user_identity LIMIT 1)")
    fun hasIdentitySync(): Boolean
}

