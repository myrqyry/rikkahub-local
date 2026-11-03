package me.rerere.locallm.litert.image.bonsai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.Closeable
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Functional image generation runtime using the upstream three-graph CPU/XNNPACK path.
 *
 * No fake output and no fallback to FLUX.2-klein on errors. Verifies all published
 * model and tokenizer bytes before the first run. Graphs are opened and closed in
 * sequence by the upstream pipeline, never resident simultaneously.
 */
class BonsaiImageRuntime(
    private val context: Context,
    private val bundle: BonsaiBundlePackage = BonsaiBundle.fromContext(context),
) : Closeable {
    private val runLock = Any()
    @Volatile private var active: BonsaiPipeline? = null

    fun missingFiles(): List<String> = bundle.missingFiles()

    fun cancel() {
        active?.cancelled = true
    }

    suspend fun generate(
        prompt: String,
        seed: Long,
        onProgress: (String) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        require(prompt.isNotBlank()) { PromptRules.EMPTY_TEXT }
        // Published Android benchmark's 256px CPU peak is ~4.6 GiB;
        // reserve margin, and never evict other applications to make room.
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val usable = info.availMem - info.threshold
        require(usable >= REQUIRED_FREE_MEMORY_BYTES) {
            "Bonsai needs at least 5.5 GiB available memory (available: ${usable / 1_048_576} MiB)"
        }
        // Keep integrity check before any tokenizer allocation or native model initialization.
        synchronized(runLock) {
            check(active == null) { "Bonsai generation already running" }
        }
        currentCoroutineContext().ensureActive()
        bundle.requireVerified()
        currentCoroutineContext().ensureActive()
        val metadataBytes = context.assets.open("bonsai/pipeline_meta.json").use { it.readBytes() }
        val metaHash = MessageDigest.getInstance("SHA-256").digest(metadataBytes)
            .joinToString("") { "%02x".format(it) }
        check(metaHash == BonsaiBundle.META_SHA256) {
            "Bonsai pipeline metadata differs from the pinned upstream revision"
        }
        val metadata = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        val tokenizer = File(bundle.root, "tokenizer")
        val engine = BonsaiPipeline(bundle.root, metadata)
        val cancellationContext = currentCoroutineContext()
        synchronized(runLock) {
            check(active == null) { "Bonsai generation already running" }
            active = engine
        }
        try {
            val codec = File(tokenizer, "vocab.json").inputStream().use { vocabulary ->
                File(tokenizer, "merges.txt").inputStream().use { merges ->
                    QwenTokenizer(vocabulary, merges)
                }
            }
            codec.let { codec ->
                require(PromptRules.refusal(prompt, codec) == null) {
                    PromptRules.refusal(prompt, codec) ?: "Invalid prompt"
                }
                val result = engine.generate(
                    tokenizer = codec, prompt = prompt.trim(), seed = seed,
                    steps = 4, size = BonsaiBundle.OUTPUT_SIZE,
                    status = { onProgress(it) },
                    progress = {
                        if (!cancellationContext.isActive) engine.cancelled = true
                        cancellationContext.ensureActive()
                    },
                )
                val pixels = IntArray(result.size * result.size)
                for (i in pixels.indices) {
                    val j = i * 3
                    pixels[i] = (0xff shl 24) or
                        ((result.rgb[j].toInt() and 0xff) shl 16) or
                        ((result.rgb[j + 1].toInt() and 0xff) shl 8) or
                        (result.rgb[j + 2].toInt() and 0xff)
                }
                Bitmap.createBitmap(pixels, result.size, result.size, Bitmap.Config.ARGB_8888)
            }
        } catch (e: BonsaiPipeline.Cancelled) {
            throw CancellationException("Bonsai generation cancelled")
        } finally {
            synchronized(runLock) { active = null }
        }
    }

    override fun close() { cancel() }

    companion object {
        const val REQUIRED_FREE_MEMORY_BYTES = 5_905_580_032L // 5.5 GiB
    }
}
