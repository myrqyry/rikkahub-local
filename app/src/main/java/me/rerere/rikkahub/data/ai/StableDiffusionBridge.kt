package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GenerationProgress(
    val step: Int,
    val totalSteps: Int,
    val elapsedMs: Long,
)

enum class GenerationPhase {
    IDLE,
    LOADING_MODEL,
    GENERATING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

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

    fun isSessionWarm(modelPath: String, backend: Backend): Boolean {
        return warmSession == (modelPath to backend.value)
    }

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

    /**
     * True while a low-memory eviction has been requested but the native release has not yet
     * run on the serialized native lane. Lifecycle callbacks set this and stop; the generation
     * flow applies the actual [invalidateSession] after the in-flight native call unwinds.
     */
    @Volatile
    var evictionRequested: Boolean = false
        internal set

    /**
     * Request a low-memory eviction of the warm session. Safe to call from any thread (lifecycle
     * callbacks): it only sets a flag and, when a generation is in flight, asks sd.cpp to stop so
     * the native context unwinds naturally. The release itself is deferred to the generation lane
     * via [evictionRequested] — never touch the native session from the callback thread.
     */
    fun requestEviction() {
        evictionRequested = true
        if (_phase.value == GenerationPhase.GENERATING) {
            nativeCancel()
        }
    }

    private val _progress = MutableStateFlow<GenerationProgress?>(null)

    val progress: StateFlow<GenerationProgress?> = _progress.asStateFlow()

    fun resetProgress() {
        _progress.value = null
    }

    private val _phase = MutableStateFlow(GenerationPhase.IDLE)

    val phase: StateFlow<GenerationPhase> = _phase.asStateFlow()

    fun setPhase(phase: GenerationPhase) {
        _phase.value = phase
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
