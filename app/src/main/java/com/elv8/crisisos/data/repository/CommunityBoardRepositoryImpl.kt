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
        val pinResolved = pinned && isNgoAlias(identity?.alias)
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

        // ANONYMOUS by spec — never put a CRS ID on the wire for community posts.
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
            senderAlias = "Anonymous",
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
                    dao.insert(
                        CommunityPostEntity(
                            id = payload.id,
                            body = payload.body,
                            category = payload.category,
                            pinned = payload.pinned,
                            createdAt = payload.createdAt,
                            expiresAt = payload.expiresAt
                        )
                    )
                }
        }
    }
}
