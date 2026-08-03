package me.rerere.tts.kitten

/**
 * Runtime knobs for Kitten TTS Nano. Kept minimal — the ONNX graph only exposes
 * voice (style embedding name), speed, and the tokenizer produces input_ids.
 */
data class KittenTtsConfig(
    val voice: String = "expr-voice-2-m",
    val speed: Float = 1.0f,
    val intraThreads: Int = 2,
) {
    init {
        require(speed in 0.25f..4.0f) { "speed must be in [0.25,4.0], got $speed" }
        require(intraThreads in 1..16) { "intra_threads must be in [1,16]" }
    }

    companion object {
        const val SAMPLE_RATE = 24000

        val AVAILABLE_VOICES: List<String> = listOf(
            "expr-voice-2-m", "expr-voice-2-f",
            "expr-voice-3-m", "expr-voice-3-f",
            "expr-voice-4-m", "expr-voice-4-f",
            "expr-voice-5-m", "expr-voice-5-f",
        )

        // Human-friendly voice labels
        val VOICE_LABELS: Map<String, String> = mapOf(
            "expr-voice-2-m" to "Voice 2 — Male",
            "expr-voice-2-f" to "Voice 2 — Female",
            "expr-voice-3-m" to "Voice 3 — Male",
            "expr-voice-3-f" to "Voice 3 — Female",
            "expr-voice-4-m" to "Voice 4 — Male",
            "expr-voice-4-f" to "Voice 4 — Female",
            "expr-voice-5-m" to "Voice 5 — Male",
            "expr-voice-5-f" to "Voice 5 — Female",
        )
    }
}
