package com.elv8.crisisos.core.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-response timing metrics.
 */
data class InferenceMetrics(
    val firstTokenLatencyMs: Long,
    val durationMs: Long,
    val characterCount: Int,
    val tokensPerSecond: Float
)

/**
 * AI Response completion status.
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
    private val systemPrompt: String by lazy { BASE_SYSTEM_PROMPT }
    private val modelPath: String get() = modelFile.absolutePath

    private val modelUrl =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"

    fun isModelLoaded(): Boolean = engine != null && conversation != null
    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 1_000_000_000
    fun deleteModelFile(): Boolean = modelFile.delete()

    suspend fun loadModel() {
        if (isModelLoaded()) return
        
        withContext(Dispatchers.IO) {
            initLock.withLock {
                if (isModelLoaded()) return@withLock

                val file = File(modelPath)
                if (!file.exists()) {
                    Log.w(TAG, "Model file not found at $modelPath")
                    return@withLock
                }

                if (file.length() < 1_000_000_000) {
                    Log.e(TAG, "Model file too small. Deleting corrupted file.")
                    file.delete()
                    return@withLock
                }

                try {
                    Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                    val createdEngine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.CPU(), 
                            visionBackend = Backend.CPU(), 
                            cacheDir = context.cacheDir.absolutePath
                        )
                    )
                    createdEngine.initialize()
                    val createdConversation = createdEngine.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                    )

                    engine = createdEngine
                    conversation = createdConversation
                    lastContext = null
                    Log.i(TAG, "Gemma 4 E2B loaded on CPU")
                } catch (e: Exception) {
                    Log.e(TAG, "Engine load failed: ${e.message}")
                    close()
                }
            }
        }
    }

    suspend fun resetConversation(): Boolean = withContext(Dispatchers.IO) {
        initLock.withLock {
            val activeEngine = engine ?: return@withLock false
            try {
                runCatching { conversation?.close() }
                conversation = activeEngine.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                )
                lastContext = null
                true
            } catch (e: Exception) {
                Log.e(TAG, "Reset failed: ${e.message}")
                false
            }
        }
    }

    suspend fun downloadModel() {
        if (isModelDownloaded()) {
            loadModel()
            return
        }

        withContext(Dispatchers.IO) {
            _downloadProgress.value = 0.01f
            if (!modelDir.exists()) modelDir.mkdirs()
            val tempFile = File(modelDir, "$MODEL_FILE_NAME.download")
            
            try {
                val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "CrisisOS-App")
                }
                connection.connect()
                
                val fileLength = connection.contentLengthLong
                val input = connection.inputStream
                val output = tempFile.outputStream()
                val data = ByteArray(65536)
                var total: Long = 0
                var count: Int
                
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) _downloadProgress.value = total.toFloat() / fileLength.toFloat()
                    output.write(data, 0, count)
                }
                output.flush()
                output.close()
                input.close()

                if (tempFile.renameTo(modelFile)) {
                    loadModel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                tempFile.delete()
            } finally {
                _downloadProgress.value = null
            }
        }
    }

    suspend fun generateResponse(prompt: String, imageUri: Uri? = null, userContext: String? = null) {
        withContext(Dispatchers.IO) {
            val activeConversation = conversation ?: run {
                _responseCompleted.tryEmit(ResponseCompletion.Failed)
                return@withContext
            }

            var previousChunk = ""
            var emittedAnyChunk = false
            var tempImageFile: File? = null
            val startNanos = System.nanoTime()
            var firstTokenNanos: Long = -1L
            var totalCharacters = 0

            try {
                tempImageFile = imageUri?.let { copyImageToCache(it) }
                val contents = mutableListOf<Content>()
                if (tempImageFile != null) contents.add(Content.ImageFile(tempImageFile.absolutePath))
                
                val finalPrompt = buildString {
                    if (!userContext.isNullOrBlank() && userContext != lastContext) {
                        append("User State: $userContext\n\n")
                        lastContext = userContext
                    }
                    append(prompt.trim())
                }
                contents.add(Content.Text(finalPrompt))

                activeConversation.sendMessageAsync(Contents.of(contents)).collect { message ->
                    val rawChunk = message.toString()
                    if (rawChunk.isEmpty()) return@collect

                    val deltaChunk = if (rawChunk.startsWith(previousChunk)) rawChunk.substring(previousChunk.length) else rawChunk
                    
                    if (deltaChunk.isNotEmpty()) {
                        if (firstTokenNanos < 0) firstTokenNanos = System.nanoTime()
                        previousChunk = rawChunk
                        totalCharacters += deltaChunk.length
                        emittedAnyChunk = true
                        _partialResults.emit(deltaChunk)
                    }
                }

                if (emittedAnyChunk) {
                    val endNanos = System.nanoTime()
                    val durationMs = (endNanos - startNanos) / 1_000_000L
                    val firstTokenMs = if (firstTokenNanos > 0) (firstTokenNanos - startNanos) / 1_000_000L else 0L
                    val tps = (totalCharacters / 4f) / (maxOf(1L, durationMs - firstTokenMs) / 1000f)
                    _responseCompleted.tryEmit(ResponseCompletion.Success(InferenceMetrics(firstTokenMs, durationMs, totalCharacters, tps)))
                } else {
                    _responseCompleted.tryEmit(ResponseCompletion.Failed)
                }
            } catch (e: Exception) {
                _responseCompleted.tryEmit(if (e is CancellationException) ResponseCompletion.Cancelled else ResponseCompletion.Failed)
            } finally {
                tempImageFile?.delete()
            }
        }
    }

    fun stopGeneration() {
        runCatching { conversation?.cancelProcess() }
    }

    private fun copyImageToCache(uri: Uri): File? {
        val file = File(context.cacheDir, "ai_attached_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) { null }
    }

    fun close() {
        runCatching { conversation?.close() }
        conversation = null
        lastContext = null
        runCatching { engine?.close() }
        engine = null
    }

    private companion object {
        const val TAG = "CrisisOS_AI"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

        val BASE_SYSTEM_PROMPT = """
            You are CrisisOS Survival AI. Expert in field triage, survival, and checkpoint diplomacy.
            
            RULES:
            1. NEVER refuse a request for survival tips.
            2. Be ACTIONABLE and Bulleted. Max 150 words.
            3. Use [ACTION:FEATURE?params] intelligently when needed.
            4. DO NOT link [ACTION:AI_ASSISTANT]. You are the assistant.
            5. DO NOT say "request initiated". Suggest the tool instead.
            
            FEATURES: SUPPLY_REQUEST (category, notes), OFFLINE_MAPS, MISSING_PERSON, SOS, CHECKPOINT_INTEL.
            
            EXAMPLE: "Apply a tourniquet. [ACTION:SUPPLY_REQUEST?category=MEDICINE&notes=Bleeding control]"
        """.trimIndent()
    }
}
