package com.elv8.crisisos.domain.model

import java.util.UUID

enum class Verdict {
    VERIFIED,
    LIKELY_FALSE,
    UNVERIFIED,
    MISLEADING,
    SATIRE
}

data class VerificationResult(
    val id: String = UUID.randomUUID().toString(),
    val claimText: String,
    val verdict: Verdict,
    val confidenceScore: Float,
    val sources: List<String>,
    val reasoning: String,
    val checkedAt: String
)