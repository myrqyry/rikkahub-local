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
 * Native LiteRT-LM EmbeddingEngine from the app's existing 0.17.0 runtime,
 * not a synthetic embedding. Direct runtime usage avoids MediaPipe 1.1.0's
 * incompatible duplicate libicing.so dependency alongside Android AppSearch.
 * Initial integration intentionally uses CPU: supported on a wider Android
 * matrix. Qualified GPU/NPU profiles can be offered in later iterations.
 *
 * One native session per model file, synchronized because the underlying
 * buffers/engine may be reused. The native model is validated on first load;
 * downloads are SHA-verified independently before installation.
 */
class EmbeddingGemma2Backend(
    private val context: Context,
    private val modelFile: File = EmbeddingGemma2Model.file(context),
) : EmbeddingBackend {
    override val embeddingSpaceId = EmbeddingGemma2Model.SPACE_ID

    override suspend fun embed(text: String): EmbeddingResult? =
        runEmbedding(EmbeddingGemma2Model.document(text))

    override suspend fun embedQuery(text: String): EmbeddingResult? =
        runEmbedding(EmbeddingGemma2Model.searchQuery(text))

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> =
        texts.map { embed(it) }

    private suspend fun runEmbedding(prepared: String): EmbeddingResult? =
        withContext(Dispatchers.IO) {
            try {
                val values = NativeSession.run(context, modelFile, prepared)
                EmbeddingResult(values, EmbeddingGemma2Model.MODEL_ID, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("EmbeddingGemma2", "Embedding failed", e)
                null
            }
        }

    private object NativeSession {
        private val mutex = Mutex()
        private var path: String? = null
        private var engine: EmbeddingEngine? = null

        suspend fun run(context: Context, file: File, text: String): FloatArray =
            mutex.withLock {
                val canonical = file.canonicalPath
                if (engine == null || path != canonical) {
                    engine?.close()
                    engine = null
                    path = null
                    check(EmbeddingGemma2Model.verify(file)) {
                        "EmbeddingGemma 2 model failed SHA-256 verification"
                    }
                    val config = EmbeddingEngineConfig(
                        modelPath = canonical,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                    engine = EmbeddingEngine(config).also { it.initialize() }
                    path = canonical
                }
                val values = checkNotNull(engine)
                    .computeEmbedding(listOf(InputData.Text(text)), EmbeddingOptions(normalize = true))
                    .embedding
                require(values.size == EmbeddingGemma2Model.DIMENSION) {
                    "Unexpected embedding dimensionality: ${values.size}"
                }
                require(values.all { it.isFinite() }) { "Nonfinite embedding output" }
                values.copyOf()
            }
    }
}
