package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted record of a fake-news check the user ran. */
@Entity(tableName = "fake_news_checks")
data class FakeNewsCheckEntity(
    @PrimaryKey val id: String,
    val claimText: String,
    val verdict: String,
    val confidenceScore: Float,
    val reasoning: String,
    val sources: String,
    val checkedAt: Long
)
