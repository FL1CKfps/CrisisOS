package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stored as TEXT enum names for forward compatibility — older builds
 * that don't know a value will decode it to a safe default via
 * the enum companion's fromStorage().
 *
 * The aggregate enum fields (threatLevel/docsRequired/waitTime) hold
 * the **majority-vote winner** computed by CheckpointRepositoryImpl.
 * The CSV tally columns (threatVotes/docsVotes/waitVotes) are the
 * raw vote counters from which the winner is recomputed on every
 * incoming report. CSV ordering matches the enum's declaration order:
 *   threatVotes  : SAFE,HOSTILE,UNKNOWN
 *   docsVotes    : NONE,ID,PASSPORT,MULTIPLE
 *   waitVotes    : UNDER_15M,FIFTEEN_TO_60M,OVER_60M,BLOCKED
 */
@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey
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
    val sourceAlias: String,
    val lastUpdated: Long,
    // ---- Feature 7 spec columns (added in MIGRATION_15_16) ----
    val threatLevel: String = "UNKNOWN",
    val docsRequired: String = "NONE",
    val waitTime: String = "UNDER_15M",
    val verifiedByNgo: Boolean = false,
    // ---- Aggregation tally columns (added in MIGRATION_16_17) ----
    val threatVotes: String = "0,0,0",
    val docsVotes: String = "0,0,0,0",
    val waitVotes: String = "0,0,0,0"
)
