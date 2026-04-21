package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.local.dao.ContactDao
import com.elv8.crisisos.data.local.dao.GroupDao
import com.elv8.crisisos.data.local.entity.ContactEntity
import com.elv8.crisisos.domain.model.contact.Contact
import com.elv8.crisisos.domain.model.contact.TrustLevel
import com.elv8.crisisos.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val groupDao: GroupDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getContactByGroup(groupId: String): Flow<List<Contact>> {
        return contactDao.getContactsByGroup(groupId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFamilyContacts(): Flow<List<Contact>> {
        return contactDao.getContactsByTrustLevel(TrustLevel.FAMILY.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getContact(crsId: String): Contact? = withContext(Dispatchers.IO) {
        // Now running in IO for DB access
        contactDao.getContactById(crsId)?.toDomain()
    }

    override suspend fun addContact(crsId: String, alias: String, avatarColor: Int) = withContext<Unit>(Dispatchers.IO) {
        val newContact = ContactEntity(
            crsId = crsId,
            alias = alias,
            addedAt = System.currentTimeMillis(),
            groupId = null,
            trustLevel = TrustLevel.BASIC.name,
            notes = "",
            avatarColor = avatarColor,
            isFavorite = false,
            isBlocked = false
        )
        contactDao.insertContact(newContact)
    }

    override suspend fun updateContact(contact: Contact) = withContext<Unit>(Dispatchers.IO) {
        contactDao.insertContact(contact.toEntity())
    }

    override suspend fun removeContact(crsId: String) = withContext<Unit>(Dispatchers.IO) {
        contactDao.getContactById(crsId)?.let {
            contactDao.deleteContact(it)
        }
    }

    override suspend fun blockContact(crsId: String) = withContext<Unit>(Dispatchers.IO) {
        contactDao.getContactById(crsId)?.let {
            val blocked = it.copy(isBlocked = true)
            contactDao.insertContact(blocked)
        }
    }

    override suspend fun setTrustLevel(crsId: String, level: TrustLevel) = withContext<Unit>(Dispatchers.IO) {
        contactDao.getContactById(crsId)?.let {
            val updated = it.copy(trustLevel = level.name)
            contactDao.insertContact(updated)
        }
    }

    override suspend fun addToGroup(crsId: String, groupId: String) = withContext<Unit>(Dispatchers.IO) {
        val group = groupDao.getGroupById(groupId) ?: return@withContext
        contactDao.getContactById(crsId)?.let {
            val updated = it.copy(groupId = groupId)
            contactDao.insertContact(updated)
        }
    }

    override suspend fun isContact(crsId: String): Boolean = withContext(Dispatchers.IO) {
        getContact(crsId) != null
    }

    private fun ContactEntity.toDomain() = Contact(
        crsId = crsId,
        alias = alias,
        addedAt = addedAt,
        groupId = groupId,
        trustLevel = try { TrustLevel.valueOf(trustLevel) } catch (e: Exception) { TrustLevel.BASIC },
        notes = notes,
        avatarColor = avatarColor,
        isFavorite = isFavorite,
        isBlocked = isBlocked
    )

    private fun Contact.toEntity() = ContactEntity(
        crsId = crsId,
        alias = alias,
        addedAt = addedAt,
        groupId = groupId,
        trustLevel = trustLevel.name,
        notes = notes,
        avatarColor = avatarColor,
        isFavorite = isFavorite,
        isBlocked = isBlocked
    )
}
