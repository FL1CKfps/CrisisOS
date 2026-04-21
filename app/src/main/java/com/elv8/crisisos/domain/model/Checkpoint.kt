package com.elv8.crisisos.domain.model

data class Checkpoint(
    val id: String,
    val name: String,
    val location: String,
    val controlledBy: String,
    val safetyRating: Int, // 1 to 5
    val isOpen: Boolean,
    val lastReport: String,
    val reportCount: Int,
    val allowsCivilians: Boolean,
    val requiresDocuments: Boolean,
    val notes: String
)
