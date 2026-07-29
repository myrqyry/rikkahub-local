package me.rerere.locallm

import java.nio.ByteBuffer

/**
 * Kotlin JNI bridge to the native LiteRT CompiledModel C++ SDK.
 *
 * Loads the native library (`litert_native_bridge`) once and delegates all
 * CompiledModel operations to C++ via `external` (JNI) functions.
 *
 * Thread-safety: the native side is not guaranteed thread-safe for concurrent
 * [run] / [runAsync] calls on the same handle. The caller (e.g. [LiteRtCompiledModelEngine])
 * is responsible for serialising access.
 */
class LiteRtNativeBridge {
    companion object {
        private var loaded = false

        /**
         * Load the native library. Safe to call multiple times — the second call is a no-op.
         * Returns `true` on success, `false` if the library could not be found or loaded.
         */
        fun loadLibrary(): Boolean {
            return try {
                System.loadLibrary("litert_native_bridge")
                loaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                loaded = false
                false
            }
        }

        /** Whether the native library has been loaded successfully. */
        fun isLoaded(): Boolean = loaded
    }

    private var initialized = false
    private var nativeHandle: Long = 0

    /**
     * Initialise a native LiteRT CompiledModel from [modelPath] using the given [delegate].
     * Throws if the native library is not loaded or if C++ initialisation fails.
     */
    fun init(modelPath: String, delegate: String) {
        require(Companion.loaded) { "Native library not loaded" }
        val handle = nativeInit(modelPath, delegate)
        if (handle == 0L) throw RuntimeException("Failed to initialize LiteRT model")
        nativeHandle = handle
        initialized = true
    }

    /** Return the model's input/output tensor metadata. */
    fun getInputOutputDetails(): LiteRtCompiledModelEngine.ModelIODetails {
        require(initialized) { "Bridge not initialized" }
        return nativeGetInputOutputDetails(nativeHandle)
    }

    /** Synchronous inference. Blocks until the native call completes. */
    fun run(inputs: Map<String, ByteBuffer>): Map<String, ByteBuffer> {
        require(initialized) { "Bridge not initialized" }
        return nativeRun(nativeHandle, inputs)
    }

    /**
     * Asynchronous inference. [callback] is invoked on a native thread when the result
     * is ready. The caller must forward to the appropriate dispatcher.
     */
    fun runAsync(inputs: Map<String, ByteBuffer>, callback: (Map<String, ByteBuffer>) -> Unit) {
        require(initialized) { "Bridge not initialized" }
        nativeRunAsync(nativeHandle, inputs, callback)
    }

    /** Release the native model handle. Safe to call multiple times. */
    fun dispose() {
        if (initialized) {
            nativeDispose(nativeHandle)
            nativeHandle = 0
            initialized = false
        }
    }

    /** JVM finalizer — ensures native resources are released if the caller forgets. */
    @Suppress("ProtectedInFinalize")
    protected fun finalize() {
        dispose()
    }

    // ---- JNI stubs ----

    private external fun nativeInit(modelPath: String, delegate: String): Long
    private external fun nativeGetInputOutputDetails(handle: Long): LiteRtCompiledModelEngine.ModelIODetails
    private external fun nativeRun(handle: Long, inputs: Map<String, ByteBuffer>): Map<String, ByteBuffer>
    private external fun nativeRunAsync(
        handle: Long,
        inputs: Map<String, ByteBuffer>,
        callback: (Map<String, ByteBuffer>) -> Unit,
    )
    private external fun nativeDispose(handle: Long)
}