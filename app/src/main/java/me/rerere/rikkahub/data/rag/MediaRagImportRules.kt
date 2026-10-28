package me.rerere.rikkahub.data.rag

import java.security.MessageDigest

enum class MediaImportFormat { IMAGE, PDF, AUDIO, VIDEO }

/** Pure, bounded rules shared by the file picker and the native media indexer. */
object MediaRagImportRules {
    const val MAX_PDF_PAGES = 24
    const val MAX_MEDIA_BYTES = 32 * 1024 * 1024
    const val MAX_WAV_BYTES = 64 * 1024 * 1024
    const val MAX_VIDEO_FRAMES = 12

    fun format(mime: String?): MediaImportFormat? = when (mime?.lowercase()) {
        "image/png", "image/jpeg", "image/jpg" -> MediaImportFormat.IMAGE
        "application/pdf" -> MediaImportFormat.PDF
        "audio/wav", "audio/x-wav", "audio/wave" -> MediaImportFormat.AUDIO
        "video/mp4" -> MediaImportFormat.VIDEO
        else -> null
    }

    fun canImport(variant: EmbeddingGemma2Variant, format: MediaImportFormat): Boolean =
        when (format) {
            MediaImportFormat.IMAGE, MediaImportFormat.PDF, MediaImportFormat.VIDEO -> variant.supportsImage
            MediaImportFormat.AUDIO -> variant.supportsAudio
        }

    fun canIndexPages(pageCount: Int): Boolean = pageCount in 1..MAX_PDF_PAGES

    /** At most twelve evenly spaced frame timestamps, all strictly inside the video. */
    fun videoTimestamps(durationMs: Long): List<Long> {
        if (durationMs <= 0L) return emptyList()
        val count = minOf(durationMs, MAX_VIDEO_FRAMES.toLong()).toInt()
        return (0 until count).map { index -> durationMs * index / count }
    }

    /** Do not store arbitrary content URIs as database IDs or leak them into search results. */
    fun vectorId(uri: String, page: Int? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "media:$digest" + (page?.let { ":page-$it" } ?: "")
    }
}
