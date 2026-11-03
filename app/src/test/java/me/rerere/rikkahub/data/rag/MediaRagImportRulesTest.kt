package me.rerere.rikkahub.data.rag

import org.junit.Assert.*
import org.junit.Test

class MediaRagImportRulesTest {
    @Test fun pdfRoutesThroughVisionEncoderAndOnlyRecognizedImagesAndWavAreAccepted() {
        assertEquals(MediaImportFormat.PDF, MediaRagImportRules.format("application/pdf"))
        assertEquals(MediaImportFormat.IMAGE, MediaRagImportRules.format("image/png"))
        assertEquals(MediaImportFormat.IMAGE, MediaRagImportRules.format("image/jpeg"))
        assertEquals(MediaImportFormat.AUDIO, MediaRagImportRules.format("audio/wav"))
        assertEquals(MediaImportFormat.AUDIO, MediaRagImportRules.format("audio/x-wav"))
        assertEquals(MediaImportFormat.VIDEO, MediaRagImportRules.format("video/mp4"))
        assertNull(MediaRagImportRules.format("audio/mpeg"))
        assertNull(MediaRagImportRules.format("text/plain"))
    }

    @Test fun stableOpaqueIdentifiersAreDistinctPerPageAndDoNotLeakRawUri() {
        val uri = "content://private.provider/files/My Photo 1.pdf"
        val one = MediaRagImportRules.vectorId(uri, 0)
        val two = MediaRagImportRules.vectorId(uri, 1)
        assertNotEquals(one, two)
        assertEquals(one, MediaRagImportRules.vectorId(uri, 0))
        assertFalse(one.contains("private.provider"))
        assertFalse(one.contains("My Photo"))
    }

    @Test fun pdfPageLimitPreventsAccidentalUnboundedProcessing() {
        assertEquals(24, MediaRagImportRules.MAX_PDF_PAGES)
        assertTrue(MediaRagImportRules.canIndexPages(24))
        assertFalse(MediaRagImportRules.canIndexPages(25))
        assertFalse(MediaRagImportRules.canIndexPages(0))
    }

    @Test fun videoSamplingIsBoundedDeterministicAndCoversTheTimeline() {
        assertEquals(12, MediaRagImportRules.MAX_VIDEO_FRAMES)
        val ticks = MediaRagImportRules.videoTimestamps(120_000L)
        assertEquals(12, ticks.size)
        assertEquals(0L, ticks.first())
        assertTrue(ticks.last() < 120_000L)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
        assertEquals(listOf(0L), MediaRagImportRules.videoTimestamps(1L))
        assertTrue(MediaRagImportRules.videoTimestamps(0L).isEmpty())
    }

    @Test fun importLabelOnlyAdvertisesFilesTheCurrentBundleCanUse() {
        assertEquals(
            "Index image, video, or PDF",
            MediaRagImportRules.importLabel(EmbeddingGemma2Variant.VISION),
        )
        assertEquals(
            "Index image, video, PDF, or WAV",
            MediaRagImportRules.importLabel(EmbeddingGemma2Variant.FULL),
        )
    }

    @Test fun mediaCapabilityMustMatchTheSelectedBundle() {
        assertFalse(MediaRagImportRules.canImport(EmbeddingGemma2Variant.TEXT, MediaImportFormat.PDF))
        assertTrue(MediaRagImportRules.canImport(EmbeddingGemma2Variant.VISION, MediaImportFormat.PDF))
        assertTrue(MediaRagImportRules.canImport(EmbeddingGemma2Variant.VISION, MediaImportFormat.IMAGE))
        assertTrue(MediaRagImportRules.canImport(EmbeddingGemma2Variant.VISION, MediaImportFormat.VIDEO))
        assertFalse(MediaRagImportRules.canImport(EmbeddingGemma2Variant.VISION, MediaImportFormat.AUDIO))
        assertTrue(MediaRagImportRules.canImport(EmbeddingGemma2Variant.FULL, MediaImportFormat.AUDIO))
    }
}
