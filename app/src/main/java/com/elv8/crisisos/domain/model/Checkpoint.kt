package com.elv8.crisisos.domain.model

/**
 * Domain-level checkpoint record. The first six fields are legacy and
 * stay for back-compat with earlier UI bindings; the bottom block
 * (threatLevel, docsRequired, waitTime, verifiedByNgo, lastUpdatedAt)
 * is the Feature-7-aligned state surfaced by the revamped screen.
 */
data class Checkpoint(
    val id: String,
    val name: String,
    val location: String,
    val controlledBy: String,
    val safetyRating: Int,
    val isOpen: Boolean,
    val lastReport: String,
    val reportCount: Int,
    val allowsCivilians: Boolean,
    val requiresDocuments: Boolean,
    val notes: String,
    // ---- Feature 7 spec fields ----
    val threatLevel: CheckpointThreat = CheckpointThreat.UNKNOWN,
    val docsRequired: DocumentsRequired = DocumentsRequired.NONE,
    val waitTime: WaitTime = WaitTime.UNDER_15M,
    val verifiedByNgo: Boolean = false,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
