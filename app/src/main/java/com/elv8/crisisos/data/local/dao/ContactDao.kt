package com.elv8.crisisos.data.local.dao

import androidx.room.*
import com.elv8.crisisos.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY alias ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isBlocked = 1")
    fun getBlockedContacts(): Flow<List<ContactEntity>>
    
    @Query("SELECT * FROM contacts WHERE groupId = :groupId")
    fun getContactsByGroup(groupId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE crsId = :crsId LIMIT 1")
    fun getContactById(crsId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE trustLevel = :trustLevel")
    fun getContactsByTrustLevel(trustLevel: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContact(contact: ContactEntity): Long

    @Delete
    fun deleteContact(contact: ContactEntity): Int
}

