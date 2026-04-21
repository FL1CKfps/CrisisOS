package com.elv8.crisisos.domain.model.media

data class MediaAttachment(
    val mediaItem: MediaItem,
    val previewUri: String,
    val isReady: Boolean
)