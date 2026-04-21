package com.elv8.crisisos.domain.model.media

sealed class MediaPickResult {
    data class Success(val mediaItem: MediaItem) : MediaPickResult()
    data class Error(val reason: PickError) : MediaPickResult()
    object Cancelled : MediaPickResult()
}

enum class PickError {
    FILE_TOO_LARGE,
    UNSUPPORTED_TYPE,
    COMPRESSION_FAILED,
    STORAGE_ERROR,
    PERMISSION_DENIED
}