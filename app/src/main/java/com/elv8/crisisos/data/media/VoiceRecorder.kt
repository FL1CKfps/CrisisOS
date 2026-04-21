package com.elv8.crisisos.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.elv8.crisisos.core.debug.MeshLogger
import com.elv8.crisisos.domain.model.media.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(
        val file: File,
        val startedAt: Long,
        val durationMs: Long = 0L,
        val amplitudeLevel: Float = 0f
    ) : RecordingState()

    data class Completed(
        val file: File,
        val durationMs: Long
    ) : RecordingState()

    data class Error(val message: String) : RecordingState()
}

@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: MediaFileManager
) {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartTime: Long = 0L
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val MAX_RECORDING_DURATION_MS = 5 * 60_000L

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val isRecording: Boolean
        get() = _state.value is RecordingState.Recording

    fun startRecording(): Boolean {
        if (isRecording) return false
        return try {
            val audioDir = fileManager.getOrCreateMediaDir(MediaType.AUDIO)
            recordingFile = File(audioDir, fileManager.generateFileName(MediaType.AUDIO, "m4a"))
            recordingStartTime = System.currentTimeMillis()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(recordingFile!!.absolutePath)
                setMaxDuration(MAX_RECORDING_DURATION_MS.toInt())
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d("CrisisOS_Recorder", "Max duration reached — stopping")
                        stopRecording()
                    }
                }
                prepare()
                start()
            }

            _state.value = RecordingState.Recording(recordingFile!!, recordingStartTime)
            startAmplitudeMonitoring()
            Log.i("CrisisOS_Recorder", "Recording started: ${recordingFile!!.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("CrisisOS_Recorder", "startRecording failed: ${e.message}")
            cleanup()
            _state.value = RecordingState.Error("Failed to start recording: ${e.message}")
            false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            amplitudeJob?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            val duration = System.currentTimeMillis() - recordingStartTime
            val file = recordingFile ?: return null

            if (duration < 500) {
                file.delete()
                _state.value = RecordingState.Idle
                Log.d("CrisisOS_Recorder", "Recording too short — discarded")
                return null
            }

            _state.value = RecordingState.Completed(file, duration)
            Log.i("CrisisOS_Recorder", "Recording completed: ${file.name} duration=${duration}ms size=${file.length()}")
            file
        } catch (e: Exception) {
            Log.e("CrisisOS_Recorder", "stopRecording failed: ${e.message}")
            _state.value = RecordingState.Error("Failed to stop: ${e.message}")
            null
        }
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        cleanup()
        recordingFile?.delete()
        recordingFile = null
        _state.value = RecordingState.Idle
        Log.d("CrisisOS_Recorder", "Recording cancelled")
    }

    private fun startAmplitudeMonitoring() {
        amplitudeJob = scope.launch {
            while (isActive && isRecording) {
                delay(80)
                val recorder = mediaRecorder ?: break
                try {
                    val maxAmplitude = recorder.maxAmplitude
                    val normalized = (maxAmplitude / 32767f).coerceIn(0f, 1f)
                    val duration = System.currentTimeMillis() - recordingStartTime
                    val current = _state.value
                    if (current is RecordingState.Recording) {
                        _state.value = current.copy(
                            durationMs = duration,
                            amplitudeLevel = normalized
                        )
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaRecorder = null
    }

    fun reset() {
        if (!isRecording) {
            _state.value = RecordingState.Idle
        }
    }

}