package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

/**
 * Geneva-Convention deconfliction filing pushed onto the mesh so other NGO
 * nodes can mirror the SHA-256 hash for tamper-proof verification.
 */
@Serializable
data class DeconflictionPayload(
    val id: String,
    val reportType: String,
    val facilityName: String,
    val coordinates: String,
    val protectionStatus: String,
    val genevaArticle: String,
    val submittedAt: Long,
    val broadcastHash: String,
    val submittedBy: String
)
