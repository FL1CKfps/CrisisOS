package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

@Serializable
data class DeadManPayload(
    val alertMessage: String,
    val registeredContacts: List<String>
)