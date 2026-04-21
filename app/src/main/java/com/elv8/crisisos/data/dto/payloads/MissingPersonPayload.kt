package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class MissingPersonPayload(
    val queryType: String, // SEARCH/REGISTER
    val crsId: String,
    val name: String,
    val age: Int?,
    val description: String?,
    val lastLocation: String?
)