package com.elv8.crisisos.domain.model.identity

import android.graphics.Color
import kotlin.random.Random

object CrsIdGenerator {
    private val CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toList()

    fun generate(): String {
        fun randomSegment(length: Int): String {
            return (1..length)
                .map { Random.nextInt(0, CHAR_POOL.size) }
                .map(CHAR_POOL::get)
                .joinToString("")
        }
        return "CRS-" + randomSegment(4) + "-" + randomSegment(4)
    }

    fun isValid(crsId: String): Boolean {
        val regex = Regex("^CRS-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}\$")
        return regex.matches(crsId)
    }

    fun generateAvatarColor(crsId: String): Int {
        val presetColors = listOf(
            Color.parseColor("#00796B"), // Teal
            Color.parseColor("#E64A19"), // Coral/Orange
            Color.parseColor("#512DA8"), // Purple
            Color.parseColor("#FFA000"), // Amber
            Color.parseColor("#1976D2"), // Blue
            Color.parseColor("#388E3C"), // Green
            Color.parseColor("#C2185B"), // Pink
            Color.parseColor("#455A64")  // Gray
        )
        val index = Math.abs(crsId.hashCode()) % presetColors.size
        return presetColors[index]
    }
}
