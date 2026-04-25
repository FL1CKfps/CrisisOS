package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.NewsItemPayload
import com.elv8.crisisos.data.local.dao.NewsItemDao
import com.elv8.crisisos.data.local.entity.NewsItemEntity
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.NewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope
) : NewsRepository {

    companion object {
        private const val NEWS_TTL_MS = 24 * 60 * 60 * 1000L
        fun isNgoAlias(alias: String?): Boolean {
            if (alias.isNullOrBlank()) return false
            return alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
        }
    }

    private val incomingStarted = AtomicBoolean(false)

    override fun observe(): Flow<List<NewsItemEntity>> =
        dao.getAllActive(System.currentTimeMillis())

    override suspend fun publish(
        headline: String,
        body: String,
        category: String,
        @Suppress("UNUSED_PARAMETER") isOfficial: Boolean
    ) {
        val now = System.currentTimeMillis()
        val identity = identityRepository.getIdentity().first()
        val sourceAlias = identity?.alias ?: "Local"
        val sourceCrsId = identity?.crsId ?: "local_device"
        // Authority enforced at repo boundary — never trust caller-supplied isOfficial.
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
}
