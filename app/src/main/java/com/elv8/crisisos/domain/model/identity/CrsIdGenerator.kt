package com.elv8.crisisos.domain.model.identity

import android.graphics.Color
import kotlin.random.Random

object CrsIdGenerator {

    fun generate(firstName: String, surname: String, dob: String): String {
        val f = firstName.take(2).uppercase().padEnd(2, 'X')
        val s = surname.take(2).uppercase().padEnd(2, 'X')
        // dob is expected to be DDMMYYYY
        return "$f$s-$dob"
    }

    fun isValid(crsId: String): Boolean {
        // Updated regex to match [A-Z]{4}-[0-9]{8}
        val regex = Regex("^[A-Z]{4}-[0-9]{8}\$")
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
