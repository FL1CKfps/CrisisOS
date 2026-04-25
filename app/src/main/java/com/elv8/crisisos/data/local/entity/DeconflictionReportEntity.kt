package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SHA-256-hashed Geneva-Convention deconfliction filing. Hash is permanent
 * tamper-proof evidence the report existed before any incident.
 */
@Entity(tableName = "deconfliction_reports")
data class DeconflictionReportEntity(
    @PrimaryKey val id: String,
    val reportType: String,
    val facilityName: String,
    val coordinates: String,
    val protectionStatus: String,
    val genevaArticle: String,
    val submittedAt: Long,
    val broadcastHash: String,
    val submittedBy: String
)
