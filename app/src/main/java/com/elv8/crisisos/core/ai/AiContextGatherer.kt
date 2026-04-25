package com.elv8.crisisos.core.ai

import com.elv8.crisisos.domain.repository.*
import com.elv8.crisisos.core.network.mesh.IMeshConnectionManager
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiContextGatherer @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val supplyRepository: SupplyRepository,
    private val contactRepository: ContactRepository,
    private val connectionManager: IMeshConnectionManager
) {
    suspend fun gather(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val identity = identityRepository.getIdentity().firstOrNull()
        val supplies = supplyRepository.getActiveRequests().firstOrNull() ?: emptyList()
        val peers = connectionManager.connectedPeers.value
        val contacts = contactRepository.getAllContacts().firstOrNull() ?: emptyList()

        // Compact representation for LLM efficiency (less tokens = faster response)
        buildString {
            append("[U:")
            if (identity != null) {
                append("${identity.alias}|ID:${identity.crsId}")
            } else {
                append("None")
            }
            append("]")
            
            if (supplies.isNotEmpty()) {
                append("[REQ:")
                append(supplies.take(3).joinToString(",") { "${it.requestType}:${it.status}" })
                append("]")
            }

            append("[MESH:${peers.size}|TRUST:${contacts.size}]")
        }
    }
}
