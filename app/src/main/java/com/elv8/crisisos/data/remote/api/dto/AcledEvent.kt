package com.elv8.crisisos.data.remote.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ACLED Read API envelope. Docs: https://acleddata.com/api-documentation/getting-started
 * The API returns `{"success":true,"count":N,"data":[...]}` for standard reads.
 */
@Serializable
data class AcledResponse(
    val success: Boolean = false,
    val count: Int = 0,
    val data: List<AcledEvent> = emptyList(),
    val error: List<String>? = null
)

@Serializable
data class AcledEvent(
    @SerialName("event_id_cnty") val eventIdCnty: String = "",
    @SerialName("event_date") val eventDate: String = "",
    val year: String = "",
    @SerialName("event_type") val eventType: String = "",
    @SerialName("sub_event_type") val subEventType: String = "",
    val actor1: String = "",
    val actor2: String = "",
    val country: String = "",
    val region: String = "",
    val admin1: String = "",
    val admin2: String = "",
    val location: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val notes: String = "",
    val fatalities: String = "0",
    val source: String = ""
)
