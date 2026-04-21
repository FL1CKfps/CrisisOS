package com.elv8.crisisos.domain.model

enum class ReportType(val label: String, val article: String) {
    MEDICAL_FACILITY("Medical Facility", "Article 19 — GC IV"),
    HUMANITARIAN_CORRIDOR("Humanitarian Corridor", "Article 23 — GC IV"),
    CIVILIAN_ZONE("Civilian Zone", "Article 15 — GC IV"),
    CULTURAL_SITE("Cultural Site", "Article 53 — AP I"),
    WATER_SOURCE("Water Source", "Article 54 — AP I")
}

enum class ProtectionStatus(val label: String) {
    PROTECTED("Protected"),
    AT_RISK("At Risk"),
    VIOLATED("Violated")
}

data class DeconflictionReport(
    val id: String,
    val reportType: ReportType,
    val facilityName: String,
    val coordinates: String,
    val protectionStatus: ProtectionStatus,
    val genevaArticle: String,
    val submittedAt: String,
    val broadcastHash: String
)