package me.rerere.locallm.litert.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Environment
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class Flux2KleinMemoryPolicy(
    val activationAllowanceBytes: Long = 2L * 1024L * 1024L * 1024L,
    val outputAllowanceBytes: Long = 2L * 1024L * 1024L,
    val safetyReserveBytes: Long = 512L * 1024L * 1024L,
) {
    fun requiredBytes(largestGraphBytes: Long, hostBytes: Long, outputBytes: Long): Long =
        largestGraphBytes + hostBytes + activationAllowanceBytes + outputBytes + safetyReserveBytes

    fun requiredBytes(pkg: Flux2KleinPackage): Long {
        val largestGraph = GRAPH_NAMES.maxOf { pkg.graph(it).length() }
        val hostBytes = BIN_NAMES.sumOf { pkg.bin(it).length() }
        return requiredBytes(largestGraph, hostBytes, outputAllowanceBytes)
    }

    fun fitsIn(requiredBytes: Long, budgetBytes: Long): Boolean = requiredBytes <= budgetBytes

    fun fitsIn(pkg: Flux2KleinPackage, budgetBytes: Long): Boolean =
        fitsIn(requiredBytes(pkg), budgetBytes)

    private companion object {
        val GRAPH_NAMES = listOf(
            "ke_enc0.tflite", "ke_enc1.tflite", "ke_enc2.tflite", "kc_prep.tflite",
            "kc_double0.tflite", "kc_double1.tflite", "kc_single0.tflite",
            "kc_single1.tflite", "kc_single2.tflite", "kc_single3.tflite",
            "kc_final.tflite", "kv_vae.tflite",
        )
        val BIN_NAMES = listOf(
            "inputs_embeds", "enc_mask", "enc_cos", "enc_sin", "cos", "sin", "temb",
            "dsigma", "bn_mean", "bn_std", "unpack_perm", "unpatch_perm", "latents0",
        )
    }
}

class LiteRtImageGenerationRuntime(
    private val context: Context,
    private val packageRoot: Flux2KleinPackage,
    private val memoryPolicy: Flux2KleinMemoryPolicy = Flux2KleinMemoryPolicy(),
    private val environmentFactory: () -> Environment = { Environment.create() },
    private val budgetProvider: () -> Long = {
        val memoryInfo = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(memoryInfo)
        (memoryInfo.availMem - memoryInfo.threshold).coerceAtLeast(0L)
    },
) : Closeable {
    private var environment: Environment? = null
    private var generator: Flux2KleinGenerator? = null

    fun status(): Flux2KleinPackageValidation = packageRoot.validate()

    suspend fun generate(prompt: String, onProgress: (String) -> Unit): Bitmap {
        val validation = status()
        require(validation.status == Flux2KleinPackageStatus.Ready) {
            when (validation.status) {
                Flux2KleinPackageStatus.ReadyBakedPrompt ->
                    "FLUX.2-klein tokenizer assets are missing; arbitrary prompts are unavailable."
                is Flux2KleinPackageStatus.NotReady -> validation.status.reason
                Flux2KleinPackageStatus.Ready -> ""
            }
        }

        val requiredBytes = memoryPolicy.requiredBytes(packageRoot)
        val budgetBytes = budgetProvider()
        require(memoryPolicy.fitsIn(requiredBytes, budgetBytes)) {
            "FLUX.2-klein needs $requiredBytes bytes of peak runtime memory, but only $budgetBytes bytes are available."
        }

        return withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val activeGenerator = synchronized(this@LiteRtImageGenerationRuntime) {
                generator ?: Flux2KleinGenerator(
                    packageRoot = packageRoot,
                    environment = environmentFactory().also { environment = it },
                ).also { generator = it }
            }
            activeGenerator.generate(prompt = prompt, onProgress = {
                coroutineContext.ensureActive()
                onProgress(it)
            })
        }
    }

    override fun close() {
        synchronized(this) {
            generator?.close()
            generator = null
            environment = null
        }
    }
}
