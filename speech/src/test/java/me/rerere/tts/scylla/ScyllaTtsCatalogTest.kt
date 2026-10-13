package me.rerere.tts.scylla

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScyllaTtsCatalogTest {
    @Test fun shippedBundleManifestHasEveryVerifiedArtifact() {
        assertEquals(16, ScyllaTtsCatalog.requiredFiles.size)
        assertEquals(5, ScyllaTtsCatalog.requiredFiles.count { it.endsWith(".tflite") })
        assertTrue(ScyllaTtsCatalog.requiredFiles.contains("manifest.json"))
        assertTrue(ScyllaTtsCatalog.downloadPairs().all { (url, relative) ->
            url.contains(ScyllaTtsCatalog.revision) && url.endsWith("/litert/$relative")
        })
        assertEquals(10, ScyllaTtsCatalog.voiceIds.size)
    }

    @Test fun voicesRestrictEnglishLocaleToSupportedAccent() {
        assertEquals("en_gb", ScyllaTtsCatalog.defaultLanguage("ink"))
        assertEquals("en_gb", ScyllaTtsCatalog.defaultLanguage("orpheus"))
        assertEquals("en_gb", ScyllaTtsCatalog.defaultLanguage("tuesday"))
        assertEquals("en_us", ScyllaTtsCatalog.defaultLanguage("scylla"))
        assertFalse("en_us" in ScyllaTtsCatalog.supportedLanguages("ink"))
        assertFalse("en_gb" in ScyllaTtsCatalog.supportedLanguages("scylla"))
        assertTrue("vi" in ScyllaTtsCatalog.supportedLanguages("tuesday"))
    }

    @Test fun providerSerializationRetainsVoiceAndDelivery() {
        val setting: TTSProviderSetting = TTSProviderSetting.ScyllaTts(
            name = "Custom Scylla",
            modelPath = "/tmp/scyllasband",
            voice = "ink",
            language = "en_gb",
            energy = 2.2f,
            tension = 1.2f,
            valence = 1.8f,
            assertiveness = 2.3f,
            seed = 512L,
            speed = 1.2f,
            steps = 6,
            sampler = "euler",
        )
        val text = Json.encodeToString<TTSProviderSetting>(setting)
        val decoded = Json.decodeFromString<TTSProviderSetting>(text)
        assertEquals(setting, decoded)
    }

    @Test fun legacyScyllaSettingsRetainNativeDefaults() {
        val original = TTSProviderSetting.ScyllaTts()
        val encoded = Json.encodeToString<TTSProviderSetting>(original)
        // Simulates a saved Scylla provider from before these controls existed.
        assertFalse(encoded.contains("\"speed\""))
        assertFalse(encoded.contains("\"steps\""))
        assertFalse(encoded.contains("\"sampler\""))
        val legacy = encoded
        val decoded = Json.decodeFromString<TTSProviderSetting>(legacy) as TTSProviderSetting.ScyllaTts
        assertEquals(1f, decoded.speed)
        assertEquals(0, decoded.steps)
        assertEquals("default", decoded.sampler)
    }

    @Test fun invalidSynthesisControlsAreRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TTSProviderSetting.ScyllaTts(speed = Float.NaN)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TTSProviderSetting.ScyllaTts(steps = 33)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TTSProviderSetting.ScyllaTts(sampler = "unavailable")
        }
    }
}
