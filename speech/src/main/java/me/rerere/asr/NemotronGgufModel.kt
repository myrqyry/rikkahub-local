package me.rerere.asr

/** Source pin verified on Pixel 10 Pro CPU (Android 17, arm64) on 2026-11-02.
 * Model weights use NVIDIA's OpenMDW-1.1 terms; not bundled in APK.
 */
object NemotronGgufModel {
    const val repo = "handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf"
    const val revision = "53ce288f69326b415aeef030cfcb02a08a8096dd"
    const val filename = "nemotron-3.5-asr-streaming-0.6b-Q5_K_M.gguf"
    const val sizeBytes = 559647200L
    const val sha256 = "86429e8c4f7fdcf9b3312269ad1ca6669478ba7805331c4aea7a2e33e9910d65"
    const val downloadUrl = "https://huggingface.co/$repo/resolve/$revision/$filename"
}
