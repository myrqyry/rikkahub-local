package me.rerere.locallm.litert.compute

/**
 * Pure memory-admission contract for the compute gate. Android adapters
 * (see [me.rerere.locallm.MemoryGuard]) read free RAM and feed this decision,
 * keeping the compute seam free of platform dependencies.
 */
sealed class MemoryAdmission {
    data object Ok : MemoryAdmission()

    data class TooLarge(
        val modelFileBytes: Long,
        val availMemBytes: Long,
        /** Total free RAM the user actually needs (model file + ~30% runtime
         *  headroom). Computed here keeps the headroom multiplier in one place
         *  and lets the UI render a coherent "need X but only Y" message. */
        val requiredFreeBytes: Long,
    ) : MemoryAdmission()
}

/** Fraction of free RAM we'll spend on the model file itself. The remaining 30%
 *  buffers the runtime's own working memory (KV cache, sampling buffers,
 *  intermediate tensors). */
private const val MODEL_BUDGET_FRACTION = 0.7

/**
 * Pure decision: loading a 4 GB model file on a device with 2 GB free OOMs the app
 * on the next allocation; we want a clean refusal envelope instead.
 */
fun decideMemoryAdmission(modelFileBytes: Long, availMemBytes: Long): MemoryAdmission {
    val budget = (availMemBytes * MODEL_BUDGET_FRACTION).toLong()
    if (modelFileBytes <= budget) return MemoryAdmission.Ok
    // Inverse of the budget formula: ceil(modelBytes / 0.7). Using ceil so the
    // reported number is never lower than what would actually be required.
    val required = ((modelFileBytes / MODEL_BUDGET_FRACTION) + 0.5).toLong()
    return MemoryAdmission.TooLarge(
        modelFileBytes = modelFileBytes,
        availMemBytes = availMemBytes,
        requiredFreeBytes = required,
    )
}
