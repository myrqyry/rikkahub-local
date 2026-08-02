package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class PocketTtsBundleTest {
    @Test
    fun requiredFilesMatchTheExportedPocketPipeline() {
        assertEquals(
            listOf(
                "text_conditioner.onnx",
                "encoder.onnx",
                "lm_main.int8.onnx",
                "lm_flow.int8.onnx",
                "decoder.int8.onnx",
                "vocab.json",
                "token_scores.json",
                "tokenizer.model",
                "manifest.json",
            ),
            PocketTtsBundle.requiredFiles,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun openingAnIncompleteBundleFailsBeforeLoadingSessions() {
        val directory = Files.createTempDirectory("pocket-tts-").toFile()
        try {
            PocketTtsBundle.open(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
