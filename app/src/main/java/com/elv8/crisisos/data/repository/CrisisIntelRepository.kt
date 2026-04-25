package com.elv8.crisisos.data.repository

import com.elv8.crisisos.data.remote.api.AcledApi
import com.elv8.crisisos.data.remote.api.GdeltApi
import com.elv8.crisisos.data.remote.api.dto.AcledEvent
import com.elv8.crisisos.data.remote.api.dto.GdeltArticle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online crisis-intel data source. Wraps GDELT 2.0 (open) and ACLED (auth) and
 * exposes them as suspend functions.
 *
 * Returns plain `List<T>` (empty on failure) instead of `Result<T>`. Reason:
 * `kotlin.Result` is an inline value class, and KSP2's Analysis API on
 * Kotlin 2.2.10 cannot synthesize a JVM descriptor for inline-class returns
 * on Hilt-processed (`@Inject`) classes — it crashes with
 * "unexpected jvm signature V". Empty lists already encode the failure case
 * the way every caller in the app uses these methods.
 */
@Singleton
class CrisisIntelRepository @Inject constructor(
    private val gdelt: GdeltApi,
    private val acled: AcledApi
) {

    /**
     * Cross-reference a free-text claim against recent global news (GDELT).
     * Returns top matching articles or an empty list on failure.
     */
    suspend fun crossReferenceClaim(
        claim: String,
        country: String?,
        timespan: String
    ): List<GdeltArticle> {
        return try {
            val q = buildString {
                append(claim.trim().take(120))
                if (!country.isNullOrBlank()) append(" sourcecountry:$country")
            }
            gdelt.searchArticles(query = q, timespan = timespan).articles
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Convenience overload — last 24h, no country filter. */
    suspend fun crossReferenceClaim(claim: String): List<GdeltArticle> =
        crossReferenceClaim(claim, null, "24h")

    /**
     * Fetch recent ACLED conflict / unrest events for a country (ISO 3166-1
     * alpha-3 or full country name as accepted by ACLED). Empty list on failure.
     */
    suspend fun recentConflictEvents(
        country: String,
        sinceDateIso: String,
        untilDateIso: String
    ): List<AcledEvent> {
        return try {
            val window = "$sinceDateIso|$untilDateIso"
            acled.readEvents(country = country, eventDate = window).data
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
