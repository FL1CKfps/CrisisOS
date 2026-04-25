package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.MeshPacketType
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.PacketParser
import com.elv8.crisisos.data.dto.payloads.CommunityPostPayload
import com.elv8.crisisos.data.local.dao.CommunityPostDao
import com.elv8.crisisos.data.local.entity.CommunityPostEntity
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.repository.CommunityBoardRepository
import com.elv8.crisisos.domain.repository.IdentityRepository
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
class CommunityBoardRepositoryImpl @Inject constructor(
    private val dao: CommunityPostDao,
    private val eventBus: EventBus,
    private val messenger: MeshMessenger,
    private val identityRepository: IdentityRepository,
    private val scope: CoroutineScope
) : CommunityBoardRepository {

    companion object {
        private const val POST_TTL_MS = 24 * 60 * 60 * 1000L
        private fun isNgoAlias(alias: String?): Boolean {
            if (alias.isNullOrBlank()) return false
            return alias.startsWith("NGO_") || alias.endsWith("_OFFICIAL")
        }
    }

    private val incomingStarted = AtomicBoolean(false)

    override fun observe(): Flow<List<CommunityPostEntity>> =
        dao.getAllActive(System.currentTimeMillis())

    override suspend fun post(body: String, category: String, pinned: Boolean) {
        // Pin can only be set by NGO accounts — enforce regardless of caller.
        val identity = identityRepository.getIdentity().first()
        val isNgo = isNgoAlias(identity?.alias)
        val pinResolved = pinned && isNgo
        val now = System.currentTimeMillis()
        val entity = CommunityPostEntity(
            id = UUID.randomUUID().toString(),
            body = body.trim(),
            category = category,
            pinned = pinResolved,
            createdAt = now,
            expiresAt = now + POST_TTL_MS
        )
        dao.insert(entity)

        // ANONYMOUS by spec — the personal CRS ID never goes on the wire.
        // For pinned posts only, we expose the NGO alias as the sender so
        // peers can verify the pin authority on ingest. The NGO alias is an
        // org tag (e.g. "NGO_OXFAM"), not a personal identifier, so this
        // preserves the anonymity guarantee for individuals while making
        // pinning authority verifiable across hops.
        val payload = CommunityPostPayload(
            id = entity.id,
            body = entity.body,
            category = entity.category,
            pinned = entity.pinned,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt
        )
        val packet = PacketFactory.buildCommunityPostPacket(
            senderId = "anonymous",
            senderAlias = if (pinResolved) identity?.alias.orEmpty() else "Anonymous",
            payload = payload
        )
        messenger.send(packet)
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        // NGO-only authority enforced at the data boundary.
        val identity = identityRepository.getIdentity().first()
        if (!isNgoAlias(identity?.alias)) {
            throw SecurityException("Only NGO accounts can pin community posts")
        }
        dao.setPinned(id, pinned)
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
                    if (event.packet.type != MeshPacketType.COMMUNITY_POST) return@collect
                    val payload = PacketParser.decodePayload(event.packet, CommunityPostPayload.serializer())
                        ?: return@collect
                    if (dao.exists(payload.id) > 0) return@collect
                    if (payload.expiresAt < System.currentTimeMillis()) return@collect
                    // Authority gate on ingest: a peer can forge `payload.pinned=true`,
                    // so we coerce it to false unless the wrapping packet's senderAlias
                    // passes the NGO heuristic. Legitimate NGO-pinned posts surface the
                    // org alias on the wire (see post() above) precisely so this check
                    // is meaningful. Until cryptographic NGO signatures land, this is
                    // the strongest verification we can do at the data boundary.
                    val pinnedResolved = payload.pinned && isNgoAlias(event.packet.senderAlias)
                    dao.insert(
                        CommunityPostEntity(
                            id = payload.id,
                            body = payload.body,
                            category = payload.category,
                            pinned = pinnedResolved,
                            createdAt = payload.createdAt,
                            expiresAt = payload.expiresAt
                        )
                    )
                }
        }
    }
}
