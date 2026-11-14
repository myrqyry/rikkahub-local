package me.rerere.tts.mediapipe

import java.io.File
import java.security.MessageDigest

/** Pinned, known-working MediaPipe TextToSpeech single-file bundles. */
data class MediaPipeTtsBundle(
    val displayName: String,
    val modelId: String,
    val engineId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val defaultVoiceId: String,
    val voices: List<Pair<String, String>>,
    val description: String,
) {
    val sourceUrl: String get() = "https://huggingface.co/$modelId"
    val downloadUrl: String get() = "$sourceUrl/resolve/main/$fileName"

    fun bundleFile(path: String): File {
        require(path.isNotBlank()) { "$displayName model directory is not configured" }
        val requested = File(path)
        val file = if (requested.isDirectory) File(requested, fileName) else requested
        require(file.name == fileName) { "Expected $fileName, got ${file.name}" }
        require(file.isFile) { "Missing $displayName model: ${file.absolutePath}" }
        require(file.length() == sizeBytes) {
            "Unexpected $displayName file size: ${file.length()} instead of $sizeBytes bytes"
        }
        return file
    }

    /** Cheap UI availability check; full SHA-256 verification is done before inference. */
    fun isInstalled(directory: File): Boolean {
        val file = File(directory, fileName)
        return file.isFile && file.length() == sizeBytes
    }

    fun verifyFile(file: File) {
        val candidate = bundleFile(file.absolutePath)
        val digest = MessageDigest.getInstance("SHA-256")
        candidate.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(sha256, ignoreCase = true)) {
            "Checksum mismatch for $displayName. Expected the official $fileName bundle"
        }
    }
}

object MediaPipeTtsCatalog {
    val PHONON = MediaPipeTtsBundle(
        displayName = "Phonon 31M (LiteRT)",
        modelId = "litert-community/phonon-31m-en",
        engineId = "mediapipe-phonon",
        fileName = "phonon_int8.litertlm",
        sizeBytes = 87_205_888,
        sha256 = "b33a9a0d65acec36762db38a2bff6d899f4aab34b196bb56d89fab5665eabebe",
        defaultVoiceId = "freya",
        voices = listOf(
            "freya" to "Freya · English",
            "claire" to "Claire · French label",
            "lukas" to "Lukas · German label",
            "sofia" to "Sofia · Spanish label",
            "lucas" to "Lucas · Portuguese label",
        ),
        description = "Compact, local 24 kHz streaming speech with five conditioning voices. English-focused; other labeled voices are not language-quality verified.",
    )

    val KOKORO = MediaPipeTtsBundle(
        displayName = "Kokoro 82M (LiteRT)",
        modelId = "litert-community/Kokoro-82M",
        engineId = "mediapipe-kokoro",
        fileName = "kokoro_int8_fp16.litertlm",
        sizeBytes = 294_783_298,
        sha256 = "e7b931ee5d4b7104012a838c9eee240d19ea69cb3ca269041a3274f83dbb7fa1",
        defaultVoiceId = "af_heart",
        voices = listOf(
            "af_heart","af_alloy","af_aoede","af_bella","af_jessica","af_kore","af_nicole","af_nova","af_river","af_sarah","af_sky",
            "am_adam","am_echo","am_eric","am_fenrir","am_liam","am_michael","am_onyx","am_puck","am_santa",
            "bf_alice","bf_emma","bf_isabella","bf_lily","bm_daniel","bm_fable","bm_george","bm_lewis",
            "ef_dora","em_alex","em_santa","ff_siwis","if_sara","im_nicola",
            "pf_dora","pm_alex","pm_santa","hf_alpha","hf_beta","hm_omega","hm_psi",
            "zf_xiaobei","zf_xiaoni","zf_xiaoxiao","zf_xiaoyi","zm_yunjian","zm_yunxi","zm_yunxia","zm_yunyang",
            "jf_alpha","jf_gongitsune","jf_nezumi","jf_tebukuro","jm_kumo",
        ).map { it to it },
        description = "54 voices across English, Spanish, French, Italian, Portuguese, Hindi, Chinese and Japanese. CPU-compatible on-device bundle.",
    )
}
