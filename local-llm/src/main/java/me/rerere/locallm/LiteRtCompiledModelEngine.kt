package me.rerere.locallm

import java.nio.ByteBuffer

/**
 * Kotlin wrapper for running non-LM LiteRT models (image gen, embedding, etc.) via
 * the [LiteRtNativeBridge] JNI layer.
 *
 * Supports CPU, GPU, NNAPI, and XNNPACK delegates. All input/output data is marshalled
 * as [ByteBuffer] for zero-copy compatibility with the native C++ [LiteRT CompiledModel] SDK.
 *
 * Usage:
 * ```kotlin
 * val engine = LiteRtCompiledModelEngine(nativeBridge, modelPath, Delegate.GPU)
 * engine.initialize().getOrThrow()
 * val output = engine.execute(mapOf("input" to byteBuffer)).getOrThrow()
 * engine.dispose()
 * ```
 */
class LiteRtCompiledModelEngine(
    private val nativeBridge: LiteRtNativeBridge,
    private val modelPath: String,
    private val delegate: Delegate = Delegate.CPU,
) {
    enum class Delegate {
        CPU,
        GPU,
        NNAPI,
        XNNPACK,
    }

    data class ModelIODetails(
        val inputs: List<IODetail>,
        val outputs: List<IODetail>,
    )

    data class IODetail(
        val name: String,
        val shape: IntArray,
        val dataType: DataType,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IODetail) return false
            return name == other.name && shape.contentEquals(other.shape) && dataType == other.dataType
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + shape.contentHashCode()
            result = 31 * result + dataType.hashCode()
            return result
        }
    }

    enum class DataType {
        FLOAT32,
        INT32,
        UINT8,
        INT8,
    }

    private var initialized = false
    private var ioDetails: ModelIODetails? = null

    /** Initialise the native LiteRT CompiledModel. Must be called before [execute]. */
    fun initialize(): Result<Unit> = runCatching {
        nativeBridge.init(modelPath, delegate.name)
        ioDetails = nativeBridge.getInputOutputDetails()
        initialized = true
    }

    /** Run a synchronous inference. [inputs] maps tensor names to their data buffers. */
    fun execute(inputs: Map<String, ByteBuffer>): Result<Map<String, ByteBuffer>> = runCatching {
        require(initialized) { "Engine not initialized" }
        nativeBridge.run(inputs)
    }

    /**
     * Run an asynchronous inference. [callback] is invoked on the native thread when the
     * result is ready. The caller must forward the result to the appropriate coroutine
     * dispatcher if needed.
     */
    fun executeAsync(
        inputs: Map<String, ByteBuffer>,
        callback: (Map<String, ByteBuffer>) -> Unit,
    ): Result<Unit> = runCatching {
        require(initialized) { "Engine not initialized" }
        nativeBridge.runAsync(inputs, callback)
    }

    /** Return the model's input/output tensor metadata. Throws if not yet initialised. */
    fun getInputOutputDetails(): ModelIODetails {
        return ioDetails ?: throw IllegalStateException("Engine not initialized")
    }

    /** Release native resources. Safe to call multiple times. */
    fun dispose() {
        if (initialized) {
            nativeBridge.dispose()
            initialized = false
        }
    }

    companion object {
        /** Convenience: pack a [FloatArray] into a direct [ByteBuffer] (4 bytes per float). */
        fun floatArrayToByteBuffer(array: FloatArray): ByteBuffer {
            val buffer = ByteBuffer.allocateDirect(array.size * 4)
            buffer.asFloatBuffer().put(array)
            buffer.rewind()
            return buffer
        }

        /** Convenience: unpack a direct [ByteBuffer] back into a [FloatArray]. */
        fun byteBufferToFloatArray(buffer: ByteBuffer): FloatArray {
            buffer.rewind()
            val array = FloatArray(buffer.remaining() / 4)
            buffer.asFloatBuffer().get(array)
            return array
        }

        /** Convenience: pack an [IntArray] into a direct [ByteBuffer] (4 bytes per int). */
        fun intArrayToByteBuffer(array: IntArray): ByteBuffer {
            val buffer = ByteBuffer.allocateDirect(array.size * 4)
            buffer.asIntBuffer().put(array)
            buffer.rewind()
            return buffer
        }
    }
}