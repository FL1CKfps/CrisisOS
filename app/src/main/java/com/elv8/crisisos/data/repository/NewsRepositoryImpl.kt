package com.elv8.crisisos.data.repository

import android.content.Context
import android.location.Geocoder
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.core.util.CredibleDomains
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.NewsItemPayload
import com.elv8.crisisos.data.local.dao.NewsItemDao
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import com.elv8.crisisos.data.remote.api.GdeltApi
import com.elv8.crisisos.data.remote.api.dto.AcledEvent
import com.elv8.crisisos.data.remote.api.dto.GdeltArticle
import com.elv8.crisisos.data.remote.api.searchArticles
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.LocationRepository
import com.elv8.crisisos.domain.repository.NewsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val dao: NewsItemDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger,
    private val identityRepository: IdentityRepository,
    private val locationRepository: LocationRepository,
    private val intel: CrisisIntelRepository,
    private val gdeltApi: GdeltApi,
    @ApplicationContext private val appContext: Context,
    private val scope: CoroutineScope
) : NewsRepository {

    companion object {
        private const val NEWS_TTL_MS = 24 * 60 * 60 * 1000L
        private const val ACLED_LOOKBACK_MS = 24 * 60 * 60 * 1000L
        private const val GDELT_TIMESPAN = "12h"
        private const val GDELT_MAX_RECORDS = 25

        // Free-text query that biases GDELT toward humanitarian / crisis
        // coverage. Combined with `sourcecountry:XX` when we have a fix.
        private const val GDELT_BASE_QUERY =
            "(humanitarian OR conflict OR evacuation OR ceasefire OR refugee OR airstrike OR aid)"

        fun isNgoAlias(alias: String?): Boolean {
            if (alias.isNullOrBlank()) return false
            return alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
        }
    }

    private val incomingStarted = AtomicBoolean(false)

    /**
     * Serializes [refreshFromOnlineSources] across all callers so that
     * concurrent invocations don't double-count `inserted` against the
     * non-atomic `dao.exists()` -> `dao.insert()` sequence.
     */
    private val refreshMutex = Mutex()

    override fun observe(): Flow<List<NewsItemEntity>> =
        dao.getAllActive(System.currentTimeMillis())

    override suspend fun publish(
        headline: String,
        body: String,
        category: String
    ) {
        val now = System.currentTimeMillis()
        val identity = identityRepository.getIdentity().first()
        val sourceAlias = identity?.alias ?: "Local"
        val sourceCrsId = identity?.crsId ?: "local_device"
        // Authority enforced at repo boundary from the authenticated identity.
        val officialResolved = isNgoAlias(identity?.alias)
        if (!officialResolved) {
            throw SecurityException("Only NGO accounts can publish CrisisNews items")
        }
        val item = NewsItemEntity(
            id = UUID.randomUUID().toString(),
            headline = headline.trim(),
            body = body.trim(),
            category = category,
            sourceAlias = sourceAlias,
            sourceCrsId = sourceCrsId,
            isOfficial = officialResolved,
            publishedAt = now,
            expiresAt = now + NEWS_TTL_MS
        )
        dao.insert(item)

        val payload = NewsItemPayload(
            id = item.id,
            headline = item.headline,
            body = item.body,
            category = item.category,
            sourceAlias = item.sourceAlias,
            sourceCrsId = item.sourceCrsId,
            isOfficial = item.isOfficial,
            publishedAt = item.publishedAt,
            expiresAt = item.expiresAt
        )
        val packet = PacketFactory.buildNewsItemPacket(
            senderId = sourceCrsId,
            senderAlias = sourceAlias,
            payload = payload
        )
        messenger.send(packet)
    }

    override suspend fun purgeExpired() {
        dao.deleteExpired(System.currentTimeMillis())
    }

    override fun observeIncoming() {
        if (!incomingStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            eventBus.events
                .filterIsInstance<AppEvent.MeshEvent.RawPacketReceived>()
                .collect { event ->
                    if (event.packet.type != MeshPacketType.CRISIS_NEWS) return@collect
                    val payload = PacketParser.decodePayload(event.packet, NewsItemPayload.serializer())
                        ?: return@collect
                    if (dao.exists(payload.id) > 0) return@collect
                    if (payload.expiresAt < System.currentTimeMillis()) return@collect
                    // Authority gate on ingest: a peer can forge `payload.isOfficial=true`
                    // and `payload.sourceAlias="NGO_*"`, so we cross-check against the
                    // wrapping packet's senderAlias (set by PacketFactory at the source
                    // device). Until cryptographic NGO signatures land, we coerce
                    // `isOfficial` to false unless BOTH the payload-claimed source and
                    // the actual sender alias pass the NGO heuristic. This matches the
                    // architect-flagged trust boundary — never honor caller-supplied
                    // privilege bits without sender verification.
                    val officialResolved = payload.isOfficial &&
                        isNgoAlias(payload.sourceAlias) &&
                        isNgoAlias(event.packet.senderAlias)
                    dao.insert(
                        NewsItemEntity(
                            id = payload.id,
                            headline = payload.headline,
                            body = payload.body,
                            category = payload.category,
                            sourceAlias = payload.sourceAlias,
                            sourceCrsId = payload.sourceCrsId,
                            isOfficial = officialResolved,
                            publishedAt = payload.publishedAt,
                            expiresAt = payload.expiresAt
                        )
                    )
                }
        }
    }

    override suspend fun refreshFromOnlineSources(): Int = withContext(Dispatchers.IO) {
        // Wrapped in a single try/runCatching so the public contract on the
        // interface ("errors are swallowed and reported as 0") is honored
        // regardless of which step (geocoder, ACLED, GDELT, or DAO) blew up.
        runCatching {
            refreshMutex.withLock {
                val now = System.currentTimeMillis()
                val (countryName, countryAlpha2) = resolveLocationContext()
                var inserted = 0

                // ----- ACLED: country-scoped conflict events (last 24h) -----
                if (!countryName.isNullOrBlank()) {
                    val today = isoDate(now)
                    val ago = isoDate(now - ACLED_LOOKBACK_MS)
                    val events = intel.recentConflictEvents(countryName, ago, today)
                    events.forEach { event ->
                        if (ingestAcled(event, now)) inserted++
                    }
                }

                // ----- GDELT: humanitarian / crisis articles (last 12h) -----
                // Filter to user's country if we have an alpha-2 code;
                // otherwise pull global so the feed isn't empty for users in
                // regions where we couldn't resolve a country.
                val query = if (!countryAlpha2.isNullOrBlank()) {
                    "$GDELT_BASE_QUERY sourcecountry:$countryAlpha2"
                } else {
                    GDELT_BASE_QUERY
                }
                val articles = runCatching {
                    gdeltApi.searchArticles(
                        query = query,
                        timespan = GDELT_TIMESPAN,
                        maxRecords = GDELT_MAX_RECORDS
                    ).articles
                }.getOrDefault(emptyList())
                articles.forEach { article ->
                    if (ingestGdelt(article, now)) inserted++
                }

                inserted
            }
        }.getOrDefault(0)
    }

    // -------------------------------------------------------------------
    //  External-source ingest helpers
    // -------------------------------------------------------------------

    private suspend fun ingestAcled(event: AcledEvent, now: Long): Boolean {
        val rawId = event.eventIdCnty.takeIf { it.isNotBlank() }
            ?: "${event.eventDate}_${event.location}_${event.eventType}".hashCode().toString()
        val id = "acled_$rawId"
        if (dao.exists(id) > 0) return false

        val locParts = listOfNotNull(
            event.location.takeIf { it.isNotBlank() },
            event.admin1.takeIf { it.isNotBlank() },
            event.country.takeIf { it.isNotBlank() }
        )
        val locStr = if (locParts.isEmpty()) "Unknown location" else locParts.joinToString(", ")
        val sub = event.subEventType.takeIf { it.isNotBlank() } ?: event.eventType
        val headline = "$sub — $locStr"

        val bodyParts = mutableListOf<String>()
        if (event.notes.isNotBlank()) bodyParts.add(event.notes.trim())
        val actors = listOfNotNull(
            event.actor1.takeIf { it.isNotBlank() },
            event.actor2.takeIf { it.isNotBlank() }
        )
        if (actors.isNotEmpty()) bodyParts.add("Actors: ${actors.joinToString(" / ")}")
        val fatalities = event.fatalities.toIntOrNull() ?: 0
        if (fatalities > 0) bodyParts.add("Reported fatalities: $fatalities")
        if (event.source.isNotBlank()) bodyParts.add("Source: ${event.source}")
        val body = bodyParts.joinToString("\n").ifBlank { "No additional details reported." }

        dao.insert(
            NewsItemEntity(
                id = id,
                headline = headline,
                body = body,
                category = mapAcledCategory(event.eventType),
                sourceAlias = "ACLED",
                sourceCrsId = "external_acled",
                // Trust boundary: third-party aggregator data is NOT first-party
                // official content. The OFFICIAL badge stays reserved for items
                // published by authenticated NGO/Camp identities. The named
                // `sourceAlias` ("ACLED" / "GDELT" domain) makes provenance
                // visible without elevating authority.
                isOfficial = false,
                publishedAt = parseAcledDate(event.eventDate, fallback = now),
                expiresAt = now + NEWS_TTL_MS
            )
        )
        return true
    }

    private suspend fun ingestGdelt(article: GdeltArticle, now: Long): Boolean {
        val urlForId = article.url.ifBlank { article.title }
        if (urlForId.isBlank()) return false
        val id = "gdelt_${urlForId.hashCode()}"
        if (dao.exists(id) > 0) return false
        if (article.title.isBlank()) return false

        val isCredible = CredibleDomains.isCredible(article.domain)
        // Skip non-credible domains entirely — GDELT pulls the long tail of
        // the open web and we don't want unverified blogs / SEO farms in the
        // crisis feed.
        if (!isCredible) return false

        val publishedAt = parseGdeltDate(article.seendate) ?: now
        dao.insert(
            NewsItemEntity(
                id = id,
                headline = article.title.trim(),
                body = "Source: ${article.domain}\n${article.url}",
                category = "ALERT",
                sourceAlias = article.domain,
                sourceCrsId = "external_gdelt",
                // External aggregator content (see ingestAcled note above).
                isOfficial = false,
                publishedAt = publishedAt,
                expiresAt = now + NEWS_TTL_MS
            )
        )
        return true
    }

    // -------------------------------------------------------------------
    //  Geocoding + date / category helpers
    // -------------------------------------------------------------------

    /**
     * Reverse-geocodes the user's last known location into
     * (countryName, countryAlpha2). Either field can be null. Geocoder is
     * called on the IO dispatcher and never throws.
     */
    private suspend fun resolveLocationContext(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val loc = locationRepository.getLastKnownLocation()
                ?: return@runCatching null to null
            if (!Geocoder.isPresent()) return@runCatching null to null
            val geocoder = Geocoder(appContext, Locale.US)
            @Suppress("DEPRECATION")
            val addr = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            addr?.countryName to addr?.countryCode
        }.getOrDefault(null to null)
    }

    private fun mapAcledCategory(eventType: String): String = when {
        eventType.contains("Battle", ignoreCase = true) -> "ALERT"
        eventType.contains("Violence", ignoreCase = true) -> "ALERT"
        eventType.contains("Explosion", ignoreCase = true) -> "ALERT"
        eventType.contains("Remote", ignoreCase = true) -> "ALERT"
        eventType.contains("Protest", ignoreCase = true) -> "SAFETY"
        eventType.contains("Riot", ignoreCase = true) -> "SAFETY"
        eventType.contains("Strategic", ignoreCase = true) -> "INFRASTRUCTURE"
        else -> "OTHER"
    }

    private fun isoDate(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(epochMs))
    }

    private fun parseAcledDate(s: String, fallback: Long): Long {
        if (s.isBlank()) return fallback
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(s)?.time ?: fallback
        }.getOrDefault(fallback)
    }

    private fun parseGdeltDate(s: String): Long? {
        if (s.isBlank()) return null
        return runCatching {
            val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(s)?.time
        }.getOrNull()
    }
}
