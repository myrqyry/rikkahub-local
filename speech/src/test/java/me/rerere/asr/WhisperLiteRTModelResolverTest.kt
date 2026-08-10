package me.rerere.asr

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLiteRTModelResolverTest {
    @Test
    fun reports_blank_missing_and_empty_paths() {
        val blank = resolveWhisperLiteRTModel(ASRProviderSetting.WhisperLiteRT(modelPath = ""))
        assertEquals(false, blank.exists)
        assertTrue(describeWhisperLiteRTModelError(ASRProviderSetting.WhisperLiteRT(), blank).contains("empty"))

        val missing = ASRProviderSetting.WhisperLiteRT(modelPath = "/missing/whisper.tflite")
        assertTrue(describeWhisperLiteRTModelError(missing).contains("not found"))

        withTempFile("whisper_tiny_30s_f32.tflite", ByteArray(0)) { file ->
            val setting = ASRProviderSetting.WhisperLiteRT(modelPath = file.absolutePath, modelId = "whisper-tiny")
            val status = resolveWhisperLiteRTModel(setting)
            assertTrue(status.empty)
            assertTrue(describeWhisperLiteRTModelError(setting, status).contains("empty"))
        }
    }

    @Test
    fun recognizes_matching_and_mismatched_catalog_files() {
        withTempFile("whisper_tiny_30s_f32.tflite", byteArrayOf(1)) { file ->
            val matching = ASRProviderSetting.WhisperLiteRT(
                modelPath = file.absolutePath,
                modelId = "whisper-tiny",
            )
            val matchingStatus = resolveWhisperLiteRTModel(matching)
            assertEquals("whisper-tiny", matchingStatus.selected?.id)
            assertEquals("whisper-tiny", matchingStatus.actual?.id)
            assertNull(matchingStatus.warning)

            val mismatched = matching.copy(modelId = "whisper-base")
            assertNotNull(resolveWhisperLiteRTModel(mismatched).warning)
        }
    }

    @Test
    fun unknown_readable_file_remains_custom() {
        withTempFile("my_custom_whisper.tflite", byteArrayOf(1)) { file ->
            val setting = ASRProviderSetting.WhisperLiteRT(
                modelPath = file.absolutePath,
                modelId = WhisperLiteRTModelCatalog.CUSTOM_ID,
            )
            val status = resolveWhisperLiteRTModel(setting)

            assertEquals(null, status.actual)
            assertNull(status.warning)
            assertEquals("Whisper LiteRT model is ready", describeWhisperLiteRTModelError(setting, status))
        }
    }

    private fun withTempFile(name: String, contents: ByteArray, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("whisper-model").toFile()
        val file = File(directory, name).apply { writeBytes(contents) }
        try {
            block(file)
        } finally {
            directory.deleteRecursively()
        }
    }
}
