package me.rerere.tts.scylla

import java.io.File
import java.security.MessageDigest

/** Publisher-pinned Scylla Band v2 LiteRT bundle (not a LiteRT-LM chat model). */
object ScyllaTtsCatalog {
    const val sourceUrl = "https://huggingface.co/spybyscript/scyllasband"
    const val revision = "0c44c02f1cad9c108ba44c520a5925495fdf7e67"
    const val sampleRate = 24_000
    val voiceIds = listOf("ariadne", "felix", "gwen", "ink", "max", "orpheus", "rex", "scylla", "stone", "tuesday")
    val languages = listOf("en_us", "en_gb", "es", "it", "fr", "de", "vi")
    private val britishVoices = setOf("ink", "orpheus", "tuesday")
    fun supportedLanguages(voice: String): List<String> =
        listOf(if (voice in britishVoices) "en_gb" else "en_us", "es", "it", "fr", "de", "vi")
    fun defaultLanguage(voice: String): String = supportedLanguages(voice).first()

    // Digest every file, not merely the manifest or size: a partial model must not be marked installed.
    val sha256 = mapOf(
        "assets/fixed_references.json" to "d3c26e7e90d90fa719254ff1ff05dc4b391cd47231b7556c3c2ed5f63547925b",
        "assets/g2p/export_config.json" to "0ec5ea81f346521f3a5c9c4a90b939c6b5143b1a11cc5e74b9902969db56e568",
        "assets/g2p/language_map.json" to "b3db2048b492799f8746b43001e0f7411eb5d359e17cabc6bfce4a7bccbfdd99",
        "assets/g2p/normalization.json" to "f9ca4fe4e4eca5e02b6f2874f5da6ede8b44b25c71c3c6ac00f96d7397024de6",
        "assets/g2p/phoneme_dict.json" to "9cfb40b2fd31d7a32351c33f90230062554a4dc0a44e89f722de211d42ed1875",
        "assets/g2p/pronunciation_overrides.json" to "a1f311379b76cb44719a2242a93a4046ff5f344cdac613374288234c2817eb59",
        "assets/g2p/tokenizer.json" to "4843d57a422b0a6045bbc6fc9a99afa329126a6742ff95ebcefcda660f3ee474",
        "assets/languages.json" to "f2af2233d0a1c0dbb6e6236fd57592d12da84262fdd1a336ead1ee83a657e2ae",
        "assets/phone_vocab.json" to "83e6457a69948edbd9a94b404c22fd408c263eb0d0b5acaafa4926e994587721",
        "assets/voices.json" to "db9aa57e3d5f9f34fed283fb8e79f4c38e218647d33bc4a5c71ea3e8338c1a56",
        "litert/duration_predictor.tflite" to "a7b063e6121ca62bc7e4940822d1016c47999c1688bd7482b27cd7396d5b5bab",
        "litert/g2p.tflite" to "a416666d1088ccb6fe59b91c7c1c343aad4374f08d4c8e21726fc7aecb79bb0c",
        "litert/vector_context_encoder.tflite" to "a7d653507b92d8a5102f4281361e8ee8f03cf001b6889e1434ad1e760fbcf057",
        "litert/vector_estimator.tflite" to "51f6ce100d8565fa7979718ca34ad0b19d1e14f6525c41c46112dfbd1d5b30cd",
        "litert/vocoder.tflite" to "a0410221c7742098fa5efea7f6f7626e522d3a761d84d8bf2e811c9d0de534f3",
        "manifest.json" to "7462b9d6ee715d8c44400994d4065b8eb635b724b6bc29ed8cbdf668e662a444",
    )
    val requiredFiles: List<String> = sha256.keys.toList()

    fun downloadPairs(): List<Pair<String, String>> = requiredFiles.map { relative ->
        "$sourceUrl/resolve/$revision/litert/$relative" to relative
    }

    fun verify(dir: File): List<String> = sha256.mapNotNull { (relative, expected) ->
        val file = File(dir, relative)
        if (!file.isFile || file.length() == 0L) return@mapNotNull relative
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buf = ByteArray(65536)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        relative.takeIf { actual != expected }
    }
}
