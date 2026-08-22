package me.rerere.locallm

import android.app.ActivityManager
import android.content.Context
import me.rerere.locallm.litert.compute.MemoryAdmission
import me.rerere.locallm.litert.compute.decideMemoryAdmission

/**
 * Android adapter for the pure memory-admission contract in the compute
 * package. Bounds-checks before loading a local model into memory: loading a
 * 4 GB model file on a device with 2 GB free OOMs the app on the next
 * allocation; we want a clean refusal envelope instead. The 0.7 multiplier
 * leaves headroom for the runtime's own working memory (KV cache, sampling
 * buffers, intermediate tensors).
 */
object MemoryGuard {

    sealed class Decision {
        data object Ok : Decision()

        data class TooLarge(
            val modelFileBytes: Long,
            val availMemBytes: Long,
            /** Total free RAM the user actually needs (model file + ~30% runtime
             *  headroom). Computed by the compute package so the headroom
             *  multiplier stays in one place. */
            val requiredFreeBytes: Long,
        ) : Decision()
    }

    /**
     * Pure decision function exposed for unit testing. The Android-aware overload
     * below reads availMem from ActivityManager and delegates here.
     */
    fun decide(modelFileBytes: Long, availMemBytes: Long): Decision =
        when (val d = decideMemoryAdmission(modelFileBytes, availMemBytes)) {
            MemoryAdmission.Ok -> Decision.Ok
            is MemoryAdmission.TooLarge -> Decision.TooLarge(d.modelFileBytes, d.availMemBytes, d.requiredFreeBytes)
        }

    /**
     * Production entry point. Reads the live availMem from ActivityManager.
     */
    fun canLoad(context: Context, modelFileBytes: Long): Decision {
        val info = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(info)
        return decide(modelFileBytes, info.availMem)
    }
}
