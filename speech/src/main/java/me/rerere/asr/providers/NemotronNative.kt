package me.rerere.asr.providers

/** All calls for a handle must be serialized on one inference worker. */
internal object NemotronNative {
    init { System.loadLibrary("nemotron_asr_android_jni") }

    external fun create(modelPath: String, language: String, threads: Int, rightContext: Int): Long
    external fun feed(handle: Long, audio16k: FloatArray): String
    external fun finish(handle: Long): String
    external fun backend(handle: Long): String
    external fun destroy(handle: Long)
}
