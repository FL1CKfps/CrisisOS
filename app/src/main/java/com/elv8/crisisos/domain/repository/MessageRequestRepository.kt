package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.chat.MessageRequest
import kotlinx.coroutines.flow.Flow

sealed class RouteResult {
    data class RoutedToThread(val threadId: String) : RouteResult()
    data class QueuedAsRequest(val requestId: String) : RouteResult()
    object Blocked : RouteResult()
    object Ignored : RouteResult() // E.g., duplicate or error
}

interface MessageRequestRepository {
    /**
     * Inspects an incoming incoming JSON message payload and decides whether
     * to append it directly to an existing thread, discard it (blocked user),
     * or queue it as a pending message request.
     */
    suspend fun routeIncomingMessage(
        fromCrsId: String,
        fromAlias: String,
        fromAvatarColor: Int,
        previewText: String,
        fullMessageJson: String
    ): RouteResult
    
    fun getPendingRequests(): Flow<List<MessageRequest>>
    fun getPendingRequestCount(): Flow<Int>

    sealed class AcceptResult {
        data class Success(val threadId: String) : AcceptResult()
        object Error : AcceptResult()
    }

    suspend fun acceptRequest(requestId: String): AcceptResult
    suspend fun rejectRequest(requestId: String)
    suspend fun deleteRequest(requestId: String)
    
    suspend fun clearExpired()
}