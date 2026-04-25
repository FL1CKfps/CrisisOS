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

/**
 * Per-response timing metrics emitted alongside completion.
 *  - firstTokenLatencyMs: time from sending the prompt to the first streamed token
 *  - durationMs: total time the response took to fully stream
 *  - characterCount: characters produced (used as a proxy for tokens since LiteRT
 *    does not surface a token count for streamed deltas)
 *  - tokensPerSecond: characterCount / 4 / seconds (4 chars ≈ 1 token rule of thumb)
 */
data class InferenceMetrics(
    val firstTokenLatencyMs: Long,
    val durationMs: Long,
    val characterCount: Int,
    val tokensPerSecond: Float
)

/**
 * Single completion event that carries metrics so collectors can attach them to the
 * exact message that just finished — no race between separate streams.
 */
sealed class ResponseCompletion {
    data class Success(val metrics: InferenceMetrics) : ResponseCompletion()
    data object Failed : ResponseCompletion()
    data object Cancelled : ResponseCompletion()
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
                lastContext = null
                Log.i(TAG, "Gemma 4 E2B model loaded successfully on CPU")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load LiteRT-LM engine: ${e.message}", e)
                // Use runCatching to avoid IllegalStateException: Engine is not initialized
                runCatching { tempEngine?.close() }
                close()
            }
        }
    }

    /**
     * Reset conversation memory without tearing down the engine.
     * Used by "New chat" — keeps the heavy engine warm but starts a fresh dialogue.
     * Returns true if successful.
     */
    suspend fun resetConversation(): Boolean = withContext(Dispatchers.IO) {
        initLock.withLock {
            val activeEngine = engine ?: return@withLock false
            try {
                runCatching { conversation?.close() }
                val fresh = activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt)
                    )
                )
                conversation = fresh
                lastContext = null
                Log.i(TAG, "Conversation reset (engine kept warm)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset conversation: ${e.message}", e)
                false
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
            _responseCompleted.tryEmit(ResponseCompletion.Failed)
            return@withContext
        }

        var previousChunk = ""
        var emittedAnyChunk = false
        var tempImageFile: File? = null

        // Performance metrics tracking
        val startNanos = System.nanoTime()
        var firstTokenNanos: Long = -1L
        var totalCharacters = 0

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
                _responseCompleted.tryEmit(ResponseCompletion.Failed)
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
                    if (firstTokenNanos < 0) {
                        firstTokenNanos = System.nanoTime()
                    }
                    previousChunk = rawChunk
                    totalCharacters += deltaChunk.length
                    emittedAnyChunk = true
                    _partialResults.emit(deltaChunk)
                }
            }

            if (emittedAnyChunk) {
                val endNanos = System.nanoTime()
                val firstTokenLatencyMs =
                    if (firstTokenNanos > 0) (firstTokenNanos - startNanos) / 1_000_000L else 0L
                val durationMs = (endNanos - startNanos) / 1_000_000L
                val streamingMs = if (firstTokenNanos > 0) {
                    (endNanos - firstTokenNanos) / 1_000_000L
                } else durationMs
                // ~4 characters per token is the common rule-of-thumb for English.
                val approxTokens = totalCharacters / 4f
                val tokensPerSecond = if (streamingMs > 0) {
                    approxTokens / (streamingMs / 1000f)
                } else 0f

                val metrics = InferenceMetrics(
                    firstTokenLatencyMs = firstTokenLatencyMs,
                    durationMs = durationMs,
                    characterCount = totalCharacters,
                    tokensPerSecond = tokensPerSecond
                )
                _responseCompleted.tryEmit(ResponseCompletion.Success(metrics))
            } else {
                _responseCompleted.tryEmit(ResponseCompletion.Failed)
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Inference cancelled")
            _responseCompleted.tryEmit(ResponseCompletion.Cancelled)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            _responseCompleted.tryEmit(ResponseCompletion.Failed)
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
            You are CrisisOS Survival AI — an on-device assistant that runs entirely offline on a refugee or aid worker's phone.

            CORE RULES:
            1. ALWAYS try to help — lives may depend on your answer. Only decline if the request would directly endanger civilians, children, or non-combatants. If you must decline, give the safest alternative path instead.
            2. Be SHORT and ACTIONABLE. Maximum 150 words. Bullet points. Markdown formatting.
            3. Lead with the single most important action ("DO THIS NOW: ...").
            4. Always end medical guidance with: "[Triage only — reach a medic if possible]".

            DOMAINS (be expert in all):
            • MEDICAL: Field triage (WHO/MSF protocols), bleeding control, CPR, dehydration, shock, fractures, burns.
            • CHECKPOINT: De-escalation phrases. ALWAYS provide the script in the target language + an English back-translation. Languages: Arabic, Hindi, Ukrainian, Russian, French, English.
            • LEGAL: Geneva Conventions and IHL civilian rights — plain language, cite the Article when known.
            • SURVIVAL: Water purification, shelter, signaling for rescue, navigation without GPS, food safety.
            • SECURITY: Spotting propaganda, recognizing manipulation, staying calm under fire.

            APP ACTIONS:
            When relevant, append exactly one tag at the very end of your reply so the app can offer a one-tap shortcut:
              [ACTION:SUPPLY_REQUEST]    — user needs water/food/medicine/shelter
              [ACTION:OFFLINE_MAPS]      — user needs a route or to find a camp
              [ACTION:MISSING_PERSON]    — user is looking for someone
              [ACTION:SOS]               — user is in immediate danger
              [ACTION:CHECKPOINT_INTEL]  — user is approaching/at a checkpoint
              [ACTION:CRISIS_NEWS]       — user is asking about evacuation orders or the situation

            TONE: Calm, authoritative, warm. The user is scared. Reassure briefly, then act.
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
