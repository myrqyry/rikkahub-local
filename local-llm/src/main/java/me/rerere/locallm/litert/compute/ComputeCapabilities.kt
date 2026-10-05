package me.rerere.locallm.litert.compute

/**
 * Pure capability snapshot consumed by the compute gate. Produced by Android
 * adapters (see [me.rerere.locallm.AcceleratorProbe]); JVM-testable. Keeping
 * the decision functions here means the compute seam has no Android surface.
 */
data class ComputeCapabilities(
    val isQualcomm: Boolean,
    val qnnLibrarySupported: Boolean,
    val gpuDelegateSupported: Boolean,
    val nnapiSupported: Boolean,
    val npuSupported: Boolean = false,
)

/**
 * Decision function for the LiteRT-LM runtime. The 0.17.0 SDK exposes CPU, GPU,
 * NPU/QNN and GOOGLE_TENSOR backends — not an NNAPI backend. QNN (Qualcomm) wins,
 * then GPU, then CPU. Returning "NNAPI" here used to be misleading because
 * LiteRtRuntime mapped unknown labels to Backend.CPU while persisting "NNAPI".
 */
fun pickLiteRt(caps: ComputeCapabilities): String = when {
    caps.isQualcomm && caps.qnnLibrarySupported -> "QNN"
    caps.gpuDelegateSupported -> "GPU"
    else -> "CPU"
}

/**
 * Decision function for the small JIT task models (image/audio classifiers, OCR,
 * detection). NPU wins over everything; then GPU; then NNAPI; CPU last. Pure and
 * JVM-testable.
 */
fun pickTaskAccelerator(caps: ComputeCapabilities): String = when {
    caps.npuSupported -> "NPU"
    caps.gpuDelegateSupported -> "GPU"
    caps.nnapiSupported -> "NNAPI"
    else -> "CPU"
}
