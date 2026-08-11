package me.rerere.asr.providers

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperByteLevelCodecTest {
    @Test
    fun decodes_gpt2_space_marker_and_utf8_bytes() {
        assertEquals(" helloé", String(
            WhisperByteLevelCodec.decodeToken("Ġhello") +
                WhisperByteLevelCodec.decodeToken("Ã©"),
            Charsets.UTF_8,
        ))
    }
}
