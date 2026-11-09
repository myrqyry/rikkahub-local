package me.rerere.locallm.litert

/**
 * A native GPU benchmark is intentionally gated by the same explicit opt-in that
 * ordinary LiteRT inference uses. GPU support detection is advisory: successful
 * initialization and generation still need separate device verification.
 */
fun canBenchmarkGpu(forceCpu: Boolean, gpuDelegateSupported: Boolean): Boolean =
    !forceCpu && gpuDelegateSupported

/** Diagnosed on Tensor G5 / LFM2.5 with LiteRT-LM 0.19.0: SDK delegate graph compilation failed. */
fun isGpuDelegateCompilationFailure(accelerator: String, message: String?): Boolean =
    accelerator.equals("gpu", ignoreCase = true) &&
        message?.contains("Failed to modify graph with delegate", ignoreCase = true) == true
