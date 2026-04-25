package com.elv8.crisisos.data.remote.api

import com.elv8.crisisos.data.remote.api.dto.AcledResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * ACLED Read API.
 *
 * Auth model (per ACLED docs):
 *   - Pass `email` + `key` (the user's API access key) on every request.
 *   - We attach those automatically via [com.elv8.crisisos.di.NetworkModule]'s
 *     interceptor so call sites only specify the *query* parameters.
 *
 * Endpoint root: https://api.acleddata.com/acled/read?... (we use json).
 *
 * NOTE: no default arguments on abstract suspend members — KSP2 (Analysis
 * API) on Kotlin 2.2.10 crashes with "unexpected jvm signature V" when
 * default-valued parameters appear on abstract methods of interfaces being
 * processed in the same compile unit. Use the extension overload below for
 * default values.
 */
interface AcledApi {

    @GET("acled/read")
    suspend fun readEvents(
        @Query("country") country: String?,
        @Query("event_date") eventDate: String?,
        @Query("event_date_where") eventDateWhere: String?,
        @Query("limit") limit: Int,
        @Query("page") page: Int
    ): AcledResponse
}

suspend fun AcledApi.readEvents(
    country: String? = null,
    eventDate: String? = null
): AcledResponse = readEvents(
    country = country,
    eventDate = eventDate,
    eventDateWhere = "BETWEEN",
    limit = 50,
    page = 1
)
