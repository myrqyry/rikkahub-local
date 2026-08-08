package me.rerere.locallm.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLibraryCatalogTest {
    @Test
    fun `all entries point at reachable verified model files`() {
        val expectedKinds = setOf(
            TaskKind.IMAGE_CLASSIFICATION,
            TaskKind.OBJECT_DETECTION,
            TaskKind.AUDIO_CLASSIFICATION,
            TaskKind.OCR,
        )
        assertEquals(expectedKinds, TaskLibraryCatalog.ENTRIES.map { it.taskKind }.toSet())
        assertTrue(TaskLibraryCatalog.ENTRIES.isNotEmpty())
    }

    @Test
    fun `every model file resolves to a huggingface url`() {
        TaskLibraryCatalog.ENTRIES.forEach { entry ->
            entry.modelFiles.forEach { file ->
                assertTrue(entry.resolveUrl(file).startsWith("https://huggingface.co/"))
            }
        }
    }

    @Test
    fun `ocr entry carries det and rec pair`() {
        val ocr = TaskLibraryCatalog.findByTaskKind(TaskKind.OCR)
        assertEquals(1, ocr.size)
        assertEquals(listOf("ppocr_det_fp16.tflite", "ppocr_rec_fp16.tflite"), ocr.first().modelFiles)
    }
}
