package com.elv8.crisisos.domain.model

/**
 * Spec-driven enums for Feature 7 (Checkpoint Threat Intelligence).
 * The values are persisted as their Kotlin name strings (e.g. "HOSTILE")
 * so DB columns and on-wire payloads stay forward/backward compatible —
 * unknown values decode to a safe default.
 */

enum class ThreatLevel {
    SAFE,      // Crowdsourced "safe" / minor friction
    HOSTILE,   // Reported aggressive, denials, harassment, danger
    UNKNOWN;   // Not enough info, or first-touch report

    companion object {
        fun fromStorage(raw: String?): ThreatLevel = when (raw?.uppercase()) {
            "SAFE" -> SAFE
            "HOSTILE" -> HOSTILE
            else -> UNKNOWN
        }
    }
}

enum class DocumentsRequired {
    NONE,
    ID,
    PASSPORT,
    MULTIPLE;

    companion object {
        fun fromStorage(raw: String?): DocumentsRequired = when (raw?.uppercase()) {
            "ID" -> ID
            "PASSPORT" -> PASSPORT
            "MULTIPLE" -> MULTIPLE
            else -> NONE
        }
    }
}

enum class WaitTime {
    UNDER_15M,        // <15 min
    FIFTEEN_TO_60M,   // 15-60 min
    OVER_60M,         // 1 hr +
    BLOCKED;          // Cannot pass at all

    companion object {
        fun fromStorage(raw: String?): WaitTime = when (raw?.uppercase()) {
            "FIFTEEN_TO_60M" -> FIFTEEN_TO_60M
            "OVER_60M" -> OVER_60M
            "BLOCKED" -> BLOCKED
            else -> UNDER_15M
        }
    }
}

/**
 * Aggregated trust level shown on each card.
 * NGO_VERIFIED — at least one NGO sender has reported this checkpoint.
 * CONFIRMED    — ≥ 2 mesh reports agree.
 * UNVERIFIED   — single report, take with caution.
 */
enum class VerificationStatus { NGO_VERIFIED, CONFIRMED, UNVERIFIED }
