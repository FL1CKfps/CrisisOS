package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.remote.api.AcledApi
import com.elv8.crisisos.data.remote.api.GdeltApi
import com.elv8.crisisos.data.remote.api.dto.AcledEvent
import com.elv8.crisisos.data.remote.api.dto.GdeltArticle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online crisis-intel data source. Wraps GDELT 2.0 (open) and ACLED (auth) and
 * exposes them as suspend functions returning safe `Result<T>` values so the
 * rest of the app can stay offline-tolerant.
 */
@Singleton
class CrisisIntelRepository @Inject constructor(
    private val gdelt: GdeltApi,
    private val acled: AcledApi
) {

    /**
     * Cross-reference a free-text claim against recent global news (GDELT).
     * Returns top matching article URLs/domains, oldest-first removed (DateDesc).
     */
    suspend fun crossReferenceClaim(
        claim: String,
        country: String? = null,
        timespan: String = "24h"
    ): Result<List<GdeltArticle>> = runCatching {
        val q = buildString {
            append(claim.trim().take(120))
            if (!country.isNullOrBlank()) append(" sourcecountry:$country")
        }
        gdelt.searchArticles(query = q, timespan = timespan).articles
    }

    /**
     * Fetch recent ACLED conflict / unrest events for a country (ISO 3166-1 alpha-3
     * or full country name as accepted by ACLED).
     */
    suspend fun recentConflictEvents(
        country: String,
        sinceDateIso: String,
        untilDateIso: String
    ): Result<List<AcledEvent>> = runCatching {
        val window = "$sinceDateIso|$untilDateIso"
        acled.readEvents(country = country, eventDate = window).data
    }
}
