package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLiteRTModelCatalogTest {
    @Test
    fun finds_portable_and_device_models() {
        assertEquals("whisper-tiny", WhisperLiteRTModelCatalog.findById("whisper-tiny")?.id)
        assertEquals("whisper-tiny-i8", WhisperLiteRTModelCatalog.findByFilename("whisper_tiny_30s_i8.tflite")?.id)
        assertEquals(
            "whisper-tiny-qualcomm-sm8550",
            WhisperLiteRTModelCatalog.findByFilename("whisper_tiny_30s_f32_Qualcomm_SM8550.tflite")?.id,
        )
        assertEquals(
            "whisper-tiny-mediatek-mt6983",
            WhisperLiteRTModelCatalog.findById("whisper-tiny-mediatek-mt6983")?.id,
        )
    }

    @Test
    fun unknown_entries_are_not_silently_selected() {
        assertNull(WhisperLiteRTModelCatalog.findById("missing"))
        assertNull(WhisperLiteRTModelCatalog.findByFilename("custom.tflite"))
        assertEquals("custom", WhisperLiteRTModelCatalog.CUSTOM_ID)
    }

    @Test
    fun entries_have_download_urls_and_license() {
        val tiny = WhisperLiteRTModelCatalog.findById("whisper-tiny")
        assertNotNull(tiny)
        assertTrue(tiny!!.downloadUrl.endsWith("/whisper_tiny_30s_f32.tflite"))
        assertEquals("apache-2.0", tiny.license)
    }
}
