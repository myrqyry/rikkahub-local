package me.rerere.tts.mediapipe

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MediaPipeTtsCatalogTest {
    @Test fun modelsAreDistinctAndPinned() {
        val phonon = MediaPipeTtsCatalog.PHONON
        val kokoro = MediaPipeTtsCatalog.KOKORO
        assertEquals("phonon_int8.litertlm", phonon.fileName)
        assertEquals("kokoro_int8_fp16.litertlm", kokoro.fileName)
        assertEquals(87_205_888L, phonon.sizeBytes)
        assertEquals(294_783_298L, kokoro.sizeBytes)
        assertEquals("freya", phonon.defaultVoiceId)
        assertEquals("af_heart", kokoro.defaultVoiceId)
        assertTrue(phonon.sourceUrl.startsWith("https://huggingface.co/litert-community/"))
        assertTrue(kokoro.sourceUrl.startsWith("https://huggingface.co/litert-community/"))
        assertEquals(64, phonon.sha256.length)
        assertEquals(64, kokoro.sha256.length)
    }
    @Test fun unrelatedFilesCannotCountAsInstalled() {
        val dir = createTempDirectory(prefix="mediapipe-test").toFile()
        try {
            File(dir, "other_model.litertlm").writeText("pretend")
            assertFalse(MediaPipeTtsCatalog.PHONON.isInstalled(dir))
            assertFalse(MediaPipeTtsCatalog.KOKORO.isInstalled(dir))
        } finally { dir.deleteRecursively() }
    }
    @Test fun kokoroHas54UniqueBundledVoiceIdentifiers() {
        val voices = MediaPipeTtsCatalog.KOKORO.voices.map { it.first }
        assertEquals(54, voices.size)
        assertEquals(54, voices.distinct().size)
        assertTrue(voices.containsAll(listOf("af_heart","bf_emma","ef_dora","jf_alpha","zf_xiaobei")))
    }

    @Test fun phononListsFivePinnedVoiceIdentifiers() {
        assertEquals(setOf("freya","claire","lukas","sofia","lucas"), MediaPipeTtsCatalog.PHONON.voices.map { it.first }.toSet())
    }
}
