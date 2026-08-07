package me.rerere.locallm.litert

/**
 * Compatibility profile for the FunctionGemma 270M Mobile Actions LiteRT-LM artifacts.
 *
 * This is runtime metadata only. It must never initiate a model download: users choose the
 * artifact on Hugging Face and install it through the existing pasted-URL model importer.
 */
object FunctionGemmaMobileActionsProfile {
    const val GENERIC_MODEL_FILE = "mobile_actions_q8_ekv1024.litertlm"
    const val GOOGLE_TENSOR_G5_MODEL_FILE =
        "functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm"

    private val knownFiles = setOf(
        GENERIC_MODEL_FILE,
        GOOGLE_TENSOR_G5_MODEL_FILE,
    )

    fun isKnownModelFile(modelFile: String): Boolean = modelFile in knownFiles

    /**
     * Runtime defaults we can safely apply today.
     *
     * The generic artifact is published as a 1024-context, dynamic-int8 CPU model and its
     * exact file size is 288,964,608 bytes. Keep the G5-compiled artifact recognized but do
     * not pretend it is interchangeable with the generic CPU/GPU build: NPU dispatch needs
     * separate validation before RikkaHub assigns backend defaults to it.
     */
    fun runtimeConfigFor(modelFile: String): LiteRtModelConfig? = when (modelFile) {
        GENERIC_MODEL_FILE -> LiteRtModelConfig(
            modelFile = GENERIC_MODEL_FILE,
            maxTokens = 1024,
            maxContextLength = 1024,
            preferredAccelerators = listOf("cpu", "gpu"),
            supportsImage = false,
            supportsAudio = false,
            supportsThinking = false,
            supportsSpeculativeDecoding = false,
            minDeviceMemoryGb = 2,
            sizeBytes = 288_964_608L,
        )

        GOOGLE_TENSOR_G5_MODEL_FILE -> null
        else -> null
    }
}
