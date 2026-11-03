package me.rerere.rikkahub.data.rag

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real LiteRT-LM 0.18.0 EmbeddingEngine with optional vision and audio encoders.
 * Uses the variant's pinned model file and a separately keyed index per bundle.
 * Runs on CPU first; do not silently substitute a cloud embedding provider.
 *
 * One guarded native session shared across model switches; the previous session
 * closes before a different model is initialized. Model checksum verification is
 * required before using the native runtime, not only after downloading.
 */
class EmbeddingGemma2Backend(
    private val context: Context,
    val variant: EmbeddingGemma2Variant = EmbeddingGemma2Variant.TEXT,
    private val modelFile: File = variant.file(context),
) : MediaEmbeddingBackend {
    override val embeddingSpaceId: String = variant.spaceId

    override suspend fun embed(text: String): EmbeddingResult? =
        runEmbedding(InputData.Text(EmbeddingGemma2Model.document(text)))

    override suspend fun embedQuery(text: String): EmbeddingResult? =
        runEmbedding(InputData.Text(EmbeddingGemma2Model.searchQuery(text)))

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> =
        texts.map { embed(it) }

    override suspend fun embedImage(imageBytes: ByteArray): EmbeddingResult? {
        require(variant.supportsImage) { "${variant.name} has no vision encoder" }
        return runEmbedding(EmbeddingGemma2MediaInput.image(imageBytes))
    }

    override suspend fun embedAudio(audioBytes: ByteArray): EmbeddingResult? {
        require(variant.supportsAudio) { "${variant.name} has no audio encoder" }
        return runEmbedding(EmbeddingGemma2MediaInput.audio(audioBytes))
    }

    private suspend fun runEmbedding(input: InputData): EmbeddingResult? =
        withContext(Dispatchers.IO) {
            try {
                val values = NativeSession.run(context, variant, modelFile, input)
                EmbeddingResult(values, variant.modelId, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("EmbeddingGemma2", "On-device embedding failed: ${variant.name}", e)
                null
            }
        }

    private object NativeSession {
        private val mutex = Mutex()
        private var path: String? = null
        private var engine: EmbeddingEngine? = null

        suspend fun run(
            context: Context,
            variant: EmbeddingGemma2Variant,
            file: File,
            input: InputData,
        ): FloatArray = mutex.withLock {
            val canonical = file.canonicalPath
            if (engine == null || path != canonical) {
                engine?.close()
                engine = null
                path = null
                check(variant.verify(file)) {
                    "EmbeddingGemma 2 ${variant.name} failed pinned SHA-256 validation"
                }
                val config = if (variant == EmbeddingGemma2Variant.TEXT) {
                    // Preserve the known text-only engine configuration unchanged.
                    EmbeddingEngineConfig(
                        modelPath = canonical,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                } else {
                    EmbeddingEngineConfig(
                        modelPath = canonical,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        audioBackend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                }
                engine = EmbeddingEngine(config).also { it.initialize() }
                path = canonical
            }
            val values = checkNotNull(engine)
                .computeEmbedding(listOf(input), EmbeddingOptions(normalize = true))
                .embedding
            require(values.size == variant.dimension) {
                "Unexpected EmbeddingGemma 2 output dimension: ${values.size}"
            }
            require(values.all { it.isFinite() }) { "Nonfinite embedding output" }
            values.copyOf()
        }
    }
}
