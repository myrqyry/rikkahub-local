package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GenerationProgress(
    val step: Int,
    val totalSteps: Int,
    val elapsedMs: Long,
)

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

    @Volatile
    private var warmSession: Pair<String, Int>? = null

    val warmModelPath: String?
        get() = warmSession?.first

    fun ensureSession(modelPath: String, backend: Backend): Boolean {
        if (warmSession == (modelPath to backend.value)) return true
        warmSession = null
        val ok = nativeInit(modelPath, backend.value)
        if (ok) warmSession = modelPath to backend.value
        return ok
    }

    fun invalidateSession() {
        warmSession = null
        if (nativeLibraryLoaded.isInitialized()) {
            nativeRelease()
        }
    }

    private val _progress = MutableStateFlow<GenerationProgress?>(null)

    val progress: StateFlow<GenerationProgress?> = _progress.asStateFlow()

    fun resetProgress() {
        _progress.value = null
    }

    @JvmStatic
    fun nativeOnProgress(step: Int, totalSteps: Int, time: Float) {
        _progress.value = GenerationProgress(
            step = step,
            totalSteps = totalSteps,
            elapsedMs = (time * 1000).toLong(),
        )
    }
}
