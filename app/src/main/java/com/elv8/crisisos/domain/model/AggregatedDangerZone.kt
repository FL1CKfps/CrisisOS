package com.elv8.crisisos.domain.model

/**
 * Source of an aggregated danger zone shown on the offline map.
 *
 * - [ACLED]        — confirmed conflict event from ACLED's public dataset.
 *                    Always treated as RED (no aggregation needed).
 * - [CROWDSOURCED] — anonymous reports submitted from the app and aggregated
 *                    by 1 km² grid + 2 h sliding window.
 */
enum class DangerSource { ACLED, CROWDSOURCED }

/**
 * A danger zone ready to be drawn on the map.
 *
 * Crowdsourced reports are grouped by the rule from CrisisOS_Context.md
 * (Feature 2 § "Crowdsourced danger zones"):
 *   - Reports from 3+ unique CRS IDs in same 1 km² grid within 2 h → RED
 *   - Single report → MEDIUM (orange, "unverified")
 *
 * Radius is purely visual. The exact polygon used for routing avoidance is
 * a circle of [radiusMeters] around ([centerLat], [centerLon]).
 */
data class AggregatedDangerZone(
    val id: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double,
    val severity: ThreatLevel,
    val title: String,
    val description: String,
    val source: DangerSource,
    val confirmedReports: Int,
    val firstReportedAt: Long,
    val lastReportedAt: Long
) {
    /**
     * Spec rule: a crowdsourced report becomes RED only after 3+ unique
     * confirmations. ACLED entries are always considered confirmed.
     */
    val isConfirmedRed: Boolean
        get() = severity == ThreatLevel.CRITICAL ||
            severity == ThreatLevel.HIGH ||
            source == DangerSource.ACLED
}
