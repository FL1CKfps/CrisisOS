package com.elv8.crisisos.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AudioPlaybackState {
    object Idle : AudioPlaybackState()
    data class Playing(val mediaId: String, val position: Long, val duration: Long) : AudioPlaybackState()
    data class Paused(val mediaId: String, val position: Long) : AudioPlaybackState()
    data class Completed(val mediaId: String) : AudioPlaybackState()
    data class Error(val mediaId: String, val message: String) : AudioPlaybackState()
}

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null
    
    private val _playbackState = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var currentMediaId: String? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun play(mediaId: String, uri: String) {
        val currentState = _playbackState.value

        if (currentMediaId == mediaId) {
            when (currentState) {
                is AudioPlaybackState.Playing -> {
                    player?.pause()
                    _playbackState.value = AudioPlaybackState.Paused(mediaId, player?.currentPosition ?: 0L)
                    stopProgressTracking()
                    return
                }
                is AudioPlaybackState.Paused, is AudioPlaybackState.Completed -> {
                    if (currentState is AudioPlaybackState.Completed) {
                        player?.seekTo(0)
                    }
                    player?.play()
                    startProgressTracking(mediaId)
                    return
                }
                else -> {} // Proceed to recreate player
            }
        }

        stop()
        
        currentMediaId = mediaId

        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            startProgressTracking(mediaId)
                        }
                        Player.STATE_ENDED -> {
                            _playbackState.value = AudioPlaybackState.Completed(mediaId)
                            stopProgressTracking()
                        }
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("CrisisOS_Audio", "Player error: ${error.message}", error)
                    _playbackState.value = AudioPlaybackState.Error(mediaId, "Playback failed")
                    stopProgressTracking()
                }
            })
        }
        
        val uriToPlay = if (uri.startsWith("content://") || uri.startsWith("file://")) {
            Uri.parse(uri)
        } else {
            Uri.fromFile(File(uri))
        }

        var retrievedDuration = 0L
        try {
            val retriever = MediaMetadataRetriever()
            if (uriToPlay.scheme == "content") {
                retriever.setDataSource(context, uriToPlay)
            } else {
                val path = if (uriToPlay.scheme == "file") uriToPlay.path else uri
                if (path != null) {
                    retriever.setDataSource(path)
                }
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                retrievedDuration = it
            }
            retriever.release()
        } catch (e: Exception) {
            Log.e("CrisisOS_Audio", "Failed to retrieve duration: ${e.message}")
        }

        Log.d("CrisisOS_Audio", "Playing URI: $uriToPlay duration=$retrievedDuration")

        _playbackState.value = AudioPlaybackState.Playing(mediaId, 0L, retrievedDuration)

        player?.setMediaItem(MediaItem.fromUri(uriToPlay))
        player?.prepare()
        player?.play()
    }

    private fun startProgressTracking(mediaId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val p = player
                if (p != null && p.isPlaying) {
                    val pos = p.currentPosition
                    var dur = p.duration
                    if (dur < 0) {
                        val current = _playbackState.value
                        if (current is AudioPlaybackState.Playing && current.duration > 0) {
                            dur = current.duration // use retriever duration if player duration is UNKNOWN
                        } else {
                            dur = 0
                        }
                    }
                    _playbackState.value = AudioPlaybackState.Playing(mediaId, pos, dur)
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun pause() {
        if (player?.isPlaying == true) {
            player?.pause()
            val mediaId = currentMediaId
            if (mediaId != null) {
                _playbackState.value = AudioPlaybackState.Paused(mediaId, player?.currentPosition ?: 0L)
            }
        }
        stopProgressTracking()
    }

    fun stop() {
        Log.d("CrisisOS_Audio", "Stopping player")
        player?.stop()
        player?.release()
        player = null
        currentMediaId = null
        _playbackState.value = AudioPlaybackState.Idle
        stopProgressTracking()
    }
}
