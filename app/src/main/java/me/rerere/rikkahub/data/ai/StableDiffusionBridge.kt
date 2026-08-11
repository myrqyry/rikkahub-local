package me.rerere.rikkahub.data.ai

object StableDiffusionBridge {
    private val nativeLibraryLoaded = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        System.loadLibrary("stablediffusion_jni")
        true
    }

    fun ensureLoaded() {
        nativeLibraryLoaded.value
    }

    enum class Backend(val value: Int) {
        CPU(0),
        VULKAN(1),
    }

    external fun nativeSupportsBackend(backend: Int): Boolean
    external fun nativeInit(modelPath: String, backend: Int): Boolean
    external fun nativeGenerate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Int,
    ): ByteArray?
    external fun nativeCancel()
    external fun nativeRelease()
}
