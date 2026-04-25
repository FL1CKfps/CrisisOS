package com.elv8.crisisos.ui.screens.missingperson

/**
 * Domain row used by [com.elv8.crisisos.domain.repository.MissingPersonRepository] and the
 * Room mapping in [com.elv8.crisisos.data.repository.MissingPersonRepositoryImpl]. Kept in
 * its own file so the unified CRS-ID lookup screen can be rewritten independently.
 */
data class RegisteredPerson(
    val crsId: String,
    val name: String,
    val age: String,
    val photoDescription: String,
    val lastKnownLocation: String,
    val registeredAt: String
)
