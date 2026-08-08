package me.rerere.locallm.task

import android.content.Context
import android.os.Build
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import java.io.File

/**
 * Thin gateway to on-device NPU inference for the small JIT task models.
 *
 * Never throws past the caller: any failure (SDK < 31, vendor libs missing, unsupported
 * SoC, corrupted model, native load error) yields `null`, and the caller falls back to its
 * existing task-library / Interpreter path. Mirrors `Qwen3TtsEngine`'s CompiledModel usage.
 *
 * CompiledModel-app scaffolding rules honored:
 *  - **Strict accelerator**: options request `Accelerator.NPU` only. A compile failure on an
 *    unsupported op yields `null` (silent fallback to the caller's baseline path), never a
 *    GPU downgrade that would masquerade as an NPU result.
 *  - **Shared Environment**: exactly one `Environment` is created per process and reused for
 *    every `CompiledModel.create` call. It is deliberately never closed.
 *
 * Note: `BuiltinNpuAcceleratorProvider` is constructed with its single-argument form, which
 * uses the default compatibility checker internally (`NpuCompatibilityChecker` companion
 * members are not visible to the Kotlin compiler for the 2.1.5 artifact).
 */
object NpuTaskInference {

    @Volatile
    private var npuReady: Boolean? = null

    @Volatile
    private var environment: Environment? = null

    /** Probed once and cached; call [resetProbe] to force a re-probe (settings Re-detect). */
    fun isNpuReady(context: Context): Boolean {
        npuReady?.let { return it }
        val result = probe(context)
        npuReady = result
        return result
    }

    fun resetProbe() {
        npuReady = null
        environment = null
    }

    private fun probe(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val provider = BuiltinNpuAcceleratorProvider(context)
            provider.isDeviceSupported() && provider.isLibraryReady()
        }.getOrDefault(false)
    }

    /** The single process-wide Environment, created lazily. Never closed (process-scoped). */
    fun environment(context: Context): Environment? {
        environment?.let { return it }
        if (!isNpuReady(context)) return null
        return runCatching {
            val provider = BuiltinNpuAcceleratorProvider(context)
            Environment.create(provider)
        }.getOrNull().also { environment = it }
    }

    /** Opens a CompiledModel requesting **strict NPU** (no GPU in the options), or null.
     *  The caller owns the returned instance and must close() it (and its buffers) in a
     *  finally block. The shared [environment] is passed to every create call. */
    fun create(context: Context, modelPath: String): CompiledModel? {
        if (!isNpuReady(context)) return null
        if (!File(modelPath).isFile) return null
        val env = environment(context) ?: return null
        return runCatching {
            CompiledModel.create(
                modelPath,
                CompiledModel.Options(Accelerator.NPU),
                env,
            )
        }.getOrNull()
    }
}
