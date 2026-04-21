package com.elv8.crisisos.domain.model

data class UserProfile(
    val userId: String = "",
    val alias: String = "",
    val deviceId: String = "",
    val emergencyContacts: List<String> = emptyList(),
    val defaultLanguage: String = "en",
    val highContrastMode: Boolean = false,
    val reducedAnimations: Boolean = false
)
