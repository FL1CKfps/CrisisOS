package com.elv8.crisisos.data.dto.payloads

import kotlinx.serialization.Serializable

/**
 * Wire format for Feature 7 checkpoint reports.
 *
 * The new Feature-7 fields (threatLevel, docsRequired, waitTime) are
 * NULLABLE on purpose so that:
 *   • peers running an older build that omits these keys still decode
 *     cleanly on a newer build (kotlinx.serialization fills nulls);
 *   • peers running a newer build still decode payloads from older
 *     senders without spurious failures.
 *
 * Privacy note: per CrisisOS_Context.md → Feature 7, we deliberately
 * never put a CRS ID in the payload. The wrapping mesh packet's
 * senderAlias is what the receiver inspects (and only to check NGO
 * verification status — never to attribute the report to an
 * individual). The `notes` field is described to the user as
 * "anonymous text note" in the report flow.
 */
@Serializable
data class CheckpointPayload(
    val checkpointName: String,
    val location: String,
    val isOpen: Boolean,
    val safetyRating: Int,
    val notes: String?,
    val threatLevel: String? = null,
    val docsRequired: String? = null,
    val waitTime: String? = null
)
