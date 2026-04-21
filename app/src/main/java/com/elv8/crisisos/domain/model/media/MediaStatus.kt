package com.elv8.crisisos.domain.model.media

enum class MediaStatus {
    PENDING,         // picked but not yet processed
    COMPRESSING,     // being compressed locally
    READY,           // local file ready, not yet sent
    SENDING,         // in outbox / being chunked for mesh
    SENT,            // confirmed sent to peer
    RECEIVED,        // received from peer, stored locally
    FAILED,          // send or receive failed
    EXPIRED          // TTL exceeded (for mesh transfer)
}
