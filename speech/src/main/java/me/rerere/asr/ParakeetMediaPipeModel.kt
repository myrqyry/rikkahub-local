package me.rerere.asr

/** Explicit opt-in download; pinned to the publisher's LiteRT-LM model revision.
 * Model: NVIDIA Parakeet TDT 0.6B v3 (CC BY 4.0); no weights bundled in the APK.
 */
object ParakeetMediaPipeModel {
    const val repo = "litert-community/parakeet-tdt-0.6b-v3"
    const val revision = "1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd"
    const val filename = "parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
    const val sizeBytes = 614588752L
    const val sha256 = "66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504"
    const val downloadUrl = "https://huggingface.co/$repo/resolve/$revision/$filename"
}
