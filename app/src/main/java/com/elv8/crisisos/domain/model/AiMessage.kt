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
    val isStreaming: Boolean = false,
    val actions: List<AiAction> = emptyList(),
    val firstTokenLatencyMs: Long? = null,
    val tokensPerSecond: Float? = null
)

data class AiAction(
    val feature: String,
    val params: Map<String, String> = emptyMap()
)
