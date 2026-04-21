package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.contact.Contact
import com.elv8.crisisos.domain.model.contact.TrustLevel
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    fun getContactByGroup(groupId: String): Flow<List<Contact>>
    fun getFamilyContacts(): Flow<List<Contact>>
    suspend fun getContact(crsId: String): Contact?
    suspend fun addContact(crsId: String, alias: String, avatarColor: Int)
    suspend fun updateContact(contact: Contact)
    suspend fun removeContact(crsId: String)
    suspend fun blockContact(crsId: String)
    suspend fun setTrustLevel(crsId: String, level: TrustLevel)
    suspend fun addToGroup(crsId: String, groupId: String)
    suspend fun isContact(crsId: String): Boolean
}