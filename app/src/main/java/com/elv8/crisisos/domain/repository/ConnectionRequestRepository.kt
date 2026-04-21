package com.elv8.crisisos.domain.repository

import com.elv8.crisisos.domain.model.connection.ConnectionRequest
import com.elv8.crisisos.domain.model.peer.Peer
import kotlinx.coroutines.flow.Flow

sealed class SendRequestResult {
    data object Success : SendRequestResult()
    data class AlreadyRequested(val existingRequestId: String) : SendRequestResult()
    data object AlreadyConnected : SendRequestResult()
    data class Error(val message: String) : SendRequestResult()
}

sealed class AcceptResult {
    data class Success(val threadId: String) : AcceptResult()
    data class Error(val message: String) : AcceptResult()
}

interface ConnectionRequestRepository {
    fun getIncomingRequests(): Flow<List<ConnectionRequest>>
    fun getOutgoingRequests(): Flow<List<ConnectionRequest>>
    fun getPendingIncomingCount(): Flow<Int>
    suspend fun sendRequest(toPeer: Peer, message: String): SendRequestResult
    suspend fun acceptRequest(requestId: String): AcceptResult
    suspend fun rejectRequest(requestId: String)
    suspend fun cancelRequest(requestId: String)
    suspend fun expireOldRequests()
}
