package me.rerere.locallm.ocr

import kotlinx.coroutines.runBlocking
import me.rerere.locallm.ocr.compiled.PpocrDetector
import me.rerere.locallm.ocr.compiled.PpocrRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PpOcrEngineTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun missingDetectionGraphFailsBeforeExecution() {
        val exception = runBlocking {
            runCatching {
                PpOcrEngine().recognize("/img.png", "/missing-det.tflite", "/rec.tflite")
            }.exceptionOrNull()
        }
        assertTrue(exception is PpOcrEngineException)
        assertTrue(exception!!.message!!.contains("detection"))
    }

    @Test
    fun missingDictionaryFailsBeforeLoadingNativeModel() {
        val det = files.newFile(PpocrDetector.MODEL)
        val rec = files.newFile(PpocrRecognizer.MODEL)
        val exception = runBlocking {
            runCatching { PpOcrEngine().recognize("/image.png", det.path, rec.path) }
                .exceptionOrNull()
        }
        assertTrue(exception is PpOcrEngineException)
        assertTrue(exception!!.message!!.contains("dictionary"))
    }

    @Test
    fun ctcRemovesBlanksAndAdjacentRepeatsButRetainsRepeatAfterBlank() {
        val chars = arrayOf("", "A", "B", " ")
        fun scores(vararg best: Int): FloatArray = FloatArray(best.size * chars.size) { at ->
            if (at % chars.size == best[at / chars.size]) 9f else -1f
        }
        assertEquals("AB", PpocrRecognizer.decode(scores(1, 1, 0, 2, 2), chars))
        assertEquals("AA", PpocrRecognizer.decode(scores(1, 0, 1), chars))
        assertEquals("A B", PpocrRecognizer.decode(scores(1, 3, 2), chars))
    }

    @Test
    fun detectorExtractsMultipleRegionsInsteadOfOneEnclosingCrop() {
        val scores = FloatArray(PpocrDetector.SIZE * PpocrDetector.SIZE)
        for (y in 50..64) for (x in 30..100) scores[y * PpocrDetector.SIZE + x] = 0.99f
        for (y in 220..238) for (x in 200..310) scores[y * PpocrDetector.SIZE + x] = 0.99f
        val boxes = PpocrDetector.boxes(scores)
        assertEquals(2, boxes.size)
        assertTrue(boxes.first().y1 < boxes.last().y0)
    }

    @Test
    fun detectorIgnoresNoiseBelowMinimumRegionSize() {
        val scores = FloatArray(PpocrDetector.SIZE * PpocrDetector.SIZE)
        scores[5 * PpocrDetector.SIZE + 5] = 1f
        assertEquals(0, PpocrDetector.boxes(scores).size)
    }
}
