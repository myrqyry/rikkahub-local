package me.rerere.rikkahub.data.ai

object StableDiffusionBridge {
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("stablediffusion_jni")
            loaded = true
        }
    }

    enum class Backend(val value: Int) {
        CPU(0),
        VULKAN(1)
    }

    external fun nativeInit(modelPath: String, backend: Int): Boolean
    external fun nativeGenerate(
        prompt: String, negativePrompt: String,
        width: Int, height: Int, steps: Int, cfg: Float, seed: Int
    ): ByteArray?
    external fun nativeRelease()
}
