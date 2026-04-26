package com.elv8.crisisos.domain.model

enum class ThreatLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN
}

data class DangerZone(
    val id: String,
    val title: String,
    val description: String,
    val threatLevel: ThreatLevel,
    val distance: String,
    val reportedBy: String,
    val timestamp: String,
    val coordinates: Pair<Double, Double>
)
