package com.elv8.crisisos.domain.usecase

import com.elv8.crisisos.domain.model.ChatMessage
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import com.elv8.crisisos.domain.repository.MeshRepository
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val meshRepository: MeshRepository
) {
    suspend operator fun invoke(content: String, senderId: String) {
        if (content.isBlank() || senderId.isBlank()) return

        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderAlias = senderId.take(5), // Simple alias generation for now
            content = content,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageStatus.SENDING,
            hopsCount = 0,
            isOwn = true,
            messageType = MessageType.TEXT
        )

        meshRepository.sendMessage(newMessage)
    }
}