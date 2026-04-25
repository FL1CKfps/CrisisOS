package com.elv8.crisisos.data.remote.api

import com.elv8.crisisos.data.remote.api.dto.GdeltDocResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GDELT 2.0 DOC API — full-text article search.
 * No auth required. Default base: https://api.gdeltproject.org/api/v2/
 *
 * Example:
 *   GET doc/doc?query=earthquake+sourcecountry:IN&mode=ArtList&format=json&maxrecords=25&timespan=24h
 */
interface GdeltApi {

    @GET("doc/doc")
    suspend fun searchArticles(
        @Query("query") query: String,
        @Query("mode") mode: String = "ArtList",
        @Query("format") format: String = "json",
        @Query("maxrecords") maxRecords: Int = 25,
        @Query("timespan") timespan: String = "24h",
        @Query("sort") sort: String = "DateDesc"
    ): GdeltDocResponse
}
