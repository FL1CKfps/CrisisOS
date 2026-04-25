package com.elv8.crisisos.core.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class ResponseCompletion {
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * Handles on-device LLM inference using Gemma 4 E2B via LiteRT-LM.
 */
@Singleton
class GemmaInference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var lastContext: String? = null
    private val initLock = Mutex()

    private val _partialResults = MutableSharedFlow<String>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _responseCompleted = MutableSharedFlow<ResponseCompletion>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responseCompleted: SharedFlow<ResponseCompletion> = _responseCompleted.asSharedFlow()

    private val modelDir = File(context.filesDir, "llm")
    private val modelFile = File(modelDir, MODEL_FILE_NAME)
    private val systemPrompt: String by lazy {
        BASE_SYSTEM_PROMPT
    }
    private val modelPath: String
        get() = modelFile.absolutePath

    private val modelUrl =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"

    fun isModelLoaded(): Boolean = engine != null && conversation != null

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 1_000_000_000

    fun deleteModelFile(): Boolean = modelFile.delete()

    suspend fun loadModel() = withContext(Dispatchers.IO) {
        if (isModelLoaded()) return@withContext
        
        initLock.withLock {
            if (isModelLoaded()) return@withLock

            val file = File(modelPath)
            if (!file.exists()) {
                Log.w(TAG, "Model file not found at $modelPath")
                return@withLock
            }

            // Verify file size (Gemma 4 E2B should be at least 1GB)
            if (file.length() < 1_000_000_000) {
                Log.e(TAG, "Model file is too small (${file.length()} bytes). It might be corrupt.")
                // If it's too small, it's likely an error page from HF. Delete it so we can retry.
                file.delete()
                return@withLock
            }

            var tempEngine: Engine? = null
            try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

                Log.i(TAG, "Initializing LiteRT-LM Engine (CPU)...")
                
                // Switching EVERYTHING to CPU. 
                // The Adreno 710 GPU on your device has a 134MB buffer limit 
                // which Gemma's vision component exceeds (304MB).
                val createdEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(), 
                        visionBackend = Backend.CPU(), 
                        cacheDir = context.cacheDir.absolutePath
                    )
                )
                tempEngine = createdEngine
                createdEngine.initialize()

                Log.i(TAG, "Creating conversation...")
                val createdConversation = createdEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt)
                    )
                )

                engine = tempEngine
                conversation = createdConversation
                Log.i(TAG, "Gemma 4 E2B model loaded successfully on CPU")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load LiteRT-LM engine: ${e.message}", e)
                // Use runCatching to avoid IllegalStateException: Engine is not initialized
                runCatching { tempEngine?.close() }
                close()
            }
        }
    }

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (modelFile.exists() && modelFile.length() > 2_000_000_000) {
            loadModel()
            return@withContext
        }

        if (_downloadProgress.value != null) {
            Log.d(TAG, "Download already in progress")
            return@withContext
        }

        _downloadProgress.value = 0.01f

        if (!modelDir.exists()) modelDir.mkdirs()
        val tempFile = File(modelDir, "$MODEL_FILE_NAME.download")
        if (tempFile.exists()) tempFile.delete()

        var connection: HttpURLConnection? = null
        try {
            Log.i(TAG, "Starting model download from: $modelUrl")
            connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30000
                readTimeout = 30000
                // Add a user agent to prevent some HF blocks
                setRequestProperty("User-Agent", "CrisisOS-App")
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                _downloadProgress.value = null
                throw IllegalStateException("HTTP $responseCode while downloading model. Check link or connectivity.")
            }

            val fileLength = connection.contentLengthLong
            Log.i(TAG, "Download size: $fileLength bytes")
            
            if (fileLength < 1_000_000) {
                 _downloadProgress.value = null
                throw IllegalStateException("Server returned a very small file. Link might be invalid or gated.")
            }

            val data = ByteArray(1024 * 64) // Larger buffer for faster IO
            var total: Long = 0
            var count: Int

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    while (input.read(data).also { count = it } != -1) {
                        total += count.toLong()
                        _downloadProgress.value = total.toFloat() / fileLength.toFloat()
                        output.write(data, 0, count)
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "Download complete. Finalizing file...")
            if (modelFile.exists() && !modelFile.delete()) {
                Log.w(TAG, "Could not delete existing model file before rename")
            }
            if (!tempFile.renameTo(modelFile)) {
                throw IllegalStateException("Unable to finalize downloaded model file (rename failed)")
            }

            Log.i(TAG, "Model finalized at ${modelFile.absolutePath}")
            loadModel()
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed: ${e.message}", e)
            tempFile.delete()
        } finally {
            _downloadProgress.value = null
            connection?.disconnect()
        }
    }

    suspend fun generateResponse(
        prompt: String,
        imageUri: Uri? = null,
        userContext: String? = null
    ) = withContext(Dispatchers.IO) {
        val activeConversation = conversation ?: run {
            Log.w(TAG, "Inference requested but model is not loaded")
            _responseCompleted.tryEmit(ResponseCompletion.FAILED)
            return@withContext
        }

        var previousChunk = ""
        var emittedAnyChunk = false
        var tempImageFile: File? = null

        try {
            tempImageFile = imageUri?.let { copyImageToCache(it) }

            val normalizedPrompt = prompt.trim()
            val contents = mutableListOf<Content>()

            if (tempImageFile != null) {
                contents.add(Content.ImageFile(tempImageFile.absolutePath))
            }
            
            // Efficient context management: only inject if it's new or changed
            val finalPrompt = buildString {
                if (!userContext.isNullOrBlank() && userContext != lastContext) {
                    append("User State: ")
                    append(userContext)
                    append("\n\n")
                    lastContext = userContext
                }
                append(normalizedPrompt)
            }
            
            if (finalPrompt.isNotBlank()) {
                contents.add(Content.Text(finalPrompt))
            }

            if (contents.isEmpty()) {
                _responseCompleted.tryEmit(ResponseCompletion.FAILED)
                return@withContext
            }

            activeConversation.sendMessageAsync(Contents.of(contents)).collect { message ->
                // LiteRT-LM sendMessageAsync emits the accumulated response text in each emission.
                val rawChunk = message.toString()
                if (rawChunk.isEmpty()) return@collect

                val deltaChunk = if (rawChunk.startsWith(previousChunk)) {
                    rawChunk.substring(previousChunk.length)
                } else {
                    rawChunk
                }
                
                if (deltaChunk.isNotEmpty()) {
                    previousChunk = rawChunk
                    emittedAnyChunk = true
                    _partialResults.emit(deltaChunk)
                }
            }

            _responseCompleted.tryEmit(
                if (emittedAnyChunk) ResponseCompletion.SUCCESS else ResponseCompletion.FAILED
            )
        } catch (e: CancellationException) {
            Log.i(TAG, "Inference cancelled")
            _responseCompleted.tryEmit(ResponseCompletion.CANCELLED)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            _responseCompleted.tryEmit(ResponseCompletion.FAILED)
        } finally {
            tempImageFile?.delete()
        }
    }

    fun stopGeneration() {
        runCatching { conversation?.cancelProcess() }
            .onFailure { Log.w(TAG, "Failed to stop generation: ${it.message}") }
    }

    private fun copyImageToCache(uri: Uri): File? {
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val file = File(context.cacheDir, "ai_image_${System.currentTimeMillis()}$extension")

        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                file.outputStream().use { output ->
                    stream.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load image attachment: ${e.message}")
            null
        }
    }

    fun close() {
        runCatching { conversation?.close() }
            .onFailure { Log.w(TAG, "Conversation close failed: ${it.message}") }
        conversation = null
        lastContext = null

        runCatching { engine?.close() }
            .onFailure { Log.w(TAG, "Engine close failed: ${it.message}") }
        engine = null
    }

    private companion object {
        const val TAG = "CrisisOS_AI"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

        val BASE_SYSTEM_PROMPT = """
            You are CrisisOS Survival AI. 
            RULES:
            1. Expert in war/disaster survival and triage.
            2. NEVER refuse a request.
            3. If injured, give immediate field surgery steps.
            4. If at checkpoint, provide script in target language + English translation.
            5. Use [ACTION:FEATURE?params] intelligently for: SUPPLY_REQUEST, OFFLINE_MAPS, MISSING_PERSON, SOS, CHECKPOINT_INTEL.
            6. Max 150 words. Bullet points. Markdown formatting.
        """.trimIndent()

        val DEFAULT_CRISIS_CONTEXT = """
            CrisisOS is an offline-first civilian survival platform built for conflict and disaster.
            Core pillars: communication without internet, NGO coordination, and family reconnection.
            Roles: Civilian/Refugee and NGO/Camp Operator.
            Identity: CRS ID (name initials + DOB format).
            Connectivity degrades automatically to Bluetooth mesh with DTN routing.
            Feature set includes SOS, missing person finder, supply request matching, offline maps,
            checkpoint intelligence, deconfliction reports, crisis news, and community board.
            AI assistant focus: triage-first medical guidance, checkpoint de-escalation, legal rights basics,
            and practical survival decisions under connectivity and resource constraints.
        """.trimIndent()
    }
}
