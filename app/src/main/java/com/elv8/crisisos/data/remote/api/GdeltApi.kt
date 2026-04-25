package com.elv8.crisisos.data.remote.api

import com.elv8.crisisos.data.remote.api.dto.GdeltDocResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GDELT 2.0 DOC API — full-text article search.
 * No auth required. Default base: https://api.gdeltproject.org/api/v2/
 *
 * NOTE: no default arguments on abstract suspend members — KSP2 (Analysis
 * API) on Kotlin 2.2.10 crashes with "unexpected jvm signature V" when
 * default-valued parameters appear on abstract methods of interfaces being
 * processed in the same compile unit. Callers pass all parameters
 * explicitly via the [searchArticles] kotlin extension below.
 */
interface GdeltApi {

    @GET("doc/doc")
    suspend fun searchArticles(
        @Query("query") query: String,
        @Query("mode") mode: String,
        @Query("format") format: String,
        @Query("maxrecords") maxRecords: Int,
        @Query("timespan") timespan: String,
        @Query("sort") sort: String
    ): GdeltDocResponse
}

/**
 * Convenience extension exposing the default parameter values without
 * declaring them on the interface itself.
 */
suspend fun GdeltApi.searchArticles(
    query: String,
    timespan: String = "24h",
    maxRecords: Int = 25
): GdeltDocResponse = searchArticles(
    query = query,
    mode = "ArtList",
    format = "json",
    maxRecords = maxRecords,
    timespan = timespan,
    sort = "DateDesc"
)
