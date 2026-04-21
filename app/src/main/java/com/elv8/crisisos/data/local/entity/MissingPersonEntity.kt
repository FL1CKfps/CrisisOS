package com.elv8.crisisos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "missing_persons")
data class MissingPersonEntity(
    @PrimaryKey val crsId: String,
    val name: String,
    val age: String,
    val photoDescription: String,
    val lastKnownLocation: String,
    val registeredAt: String
)