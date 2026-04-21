package com.elv8.crisisos.domain.model

import java.util.UUID

enum class AiRole {
    USER, ASSISTANT, SYSTEM
}

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AiRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)