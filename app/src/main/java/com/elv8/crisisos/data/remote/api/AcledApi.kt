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
 * Endpoint root: https://api.acleddata.com/acled/read.csv?... (we use json).
 */
interface AcledApi {

    @GET("acled/read")
    suspend fun readEvents(
        @Query("country") country: String? = null,
        @Query("event_date") eventDate: String? = null, // e.g. "2026-04-25" or "2026-04-01|2026-04-25"
        @Query("event_date_where") eventDateWhere: String? = "BETWEEN",
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1
    ): AcledResponse
}
