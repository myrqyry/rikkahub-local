package me.rerere.rikkahub.data.rag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MediaIngestionResult(
    val name: String,
    val format: MediaImportFormat,
    val indexed: Int,
    val total: Int,
)

/**
 * On-device SAF file ingest. Visual PDF pages are rasterized before embedding;
 * PDFs are NOT treated as plain extracted text or a native LiteRT PDF input.
 * The originals remain with the document provider; this stores vectors and
 * metadata only. Never uploads the source material.
 */
class MediaRagIngestion(
    private val context: Context,
    private val repository: EmbeddingRepository,
) {
    suspend fun ingest(uri: Uri, variant: EmbeddingGemma2Variant): MediaIngestionResult =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val format = MediaRagImportRules.format(resolver.getType(uri))
                ?: error("Choose a PNG, JPEG, WAV, MP4, or PDF file")
            require(MediaRagImportRules.canImport(variant, format)) {
                "${variant.name} cannot embed ${format.name}; select a compatible bundle"
            }
            // Selection verifies SHA; native first-load verifies it again. Avoid a
            // redundant 388/485 MB hash scan on every import, even with a warm engine.
            require(variant.present(context)) {
                "Selected EmbeddingGemma 2 model file is missing or incomplete"
            }
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }?.take(160)?.takeIf { it.isNotBlank() } ?: "Imported ${format.name.lowercase()}"
            val opaqueSource = "${variant.spaceId}:${uri}"
            val metadataName = name.replace(Regex("[\\r\\n]"), " ")
            when (format) {
                MediaImportFormat.IMAGE, MediaImportFormat.AUDIO -> {
                    val limit = if (format == MediaImportFormat.AUDIO) {
                        MediaRagImportRules.MAX_WAV_BYTES
                    } else MediaRagImportRules.MAX_MEDIA_BYTES
                    val bytes = resolver.openInputStream(uri)?.use { readLimited(it, limit) }
                        ?: error("Unable to read media file")
                    val kind = if (format == MediaImportFormat.IMAGE) {
                        MediaEmbeddingKind.IMAGE
                    } else MediaEmbeddingKind.AUDIO
                    val ok = repository.indexMedia(
                        id = MediaRagImportRules.vectorId(opaqueSource),
                        bytes = bytes,
                        kind = kind,
                        metadata = buildJsonObject {
                            put("text", metadataName)
                            put("sourceName", metadataName)
                            put("sourceUri", uri.toString())
                            put("mediaType", format.name.lowercase())
                        },
                        expectedSpaceId = variant.spaceId,
                    )
                    check(ok) { "On-device media embedding failed; nothing was indexed" }
                    MediaIngestionResult(name, format, 1, 1)
                }
                MediaImportFormat.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val durationMs = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: error("Video duration is unavailable")
                        require(durationMs in 1L..1_800_000L) {
                            "Video must be between 1 ms and 30 minutes"
                        }
                        val timestamps = MediaRagImportRules.videoTimestamps(durationMs)
                        val sourceWidth = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                        )?.toIntOrNull() ?: 0
                        val sourceHeight = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                        )?.toIntOrNull() ?: 0
                        val dimension = max(sourceWidth, sourceHeight)
                        val scale = if (dimension > 0) minOf(1.0, 1024.0 / dimension) else 1.0
                        val targetWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
                        val targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
                        var indexed = 0
                        timestamps.forEachIndexed { frameIndex, timeMs ->
                            currentCoroutineContext().ensureActive()
                            val frame = if (Build.VERSION.SDK_INT >= 27 && sourceWidth > 0 &&
                                sourceHeight > 0) {
                                retriever.getScaledFrameAtTime(
                                    timeMs * 1000L,
                                    MediaMetadataRetriever.OPTION_CLOSEST,
                                    targetWidth,
                                    targetHeight,
                                )
                            } else {
                                retriever.getFrameAtTime(
                                    timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST
                                )
                            } ?: error("Could not extract video frame ${frameIndex + 1}")
                            val bounded = if (max(frame.width, frame.height) > 1024) {
                                val ratio = 1024.0 / max(frame.width, frame.height)
                                Bitmap.createScaledBitmap(
                                    frame,
                                    (frame.width * ratio).toInt().coerceAtLeast(1),
                                    (frame.height * ratio).toInt().coerceAtLeast(1),
                                    true,
                                )
                            } else frame
                            val jpeg = try {
                                ByteArrayOutputStream().use { output ->
                                    check(bounded.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                                        "Cannot encode video frame ${frameIndex + 1}"
                                    }
                                    output.toByteArray()
                                }
                            } finally {
                                if (bounded !== frame) bounded.recycle()
                                frame.recycle()
                            }
                            val ok = repository.indexMedia(
                                id = MediaRagImportRules.vectorId(opaqueSource, frameIndex),
                                bytes = jpeg,
                                kind = MediaEmbeddingKind.IMAGE,
                                metadata = buildJsonObject {
                                    put("text", "$metadataName · video frame at ${timeMs / 1000}s")
                                    put("sourceName", metadataName)
                                    put("sourceUri", uri.toString())
                                    put("timeMs", timeMs)
                                    put("mediaType", "video")
                                },
                                expectedSpaceId = variant.spaceId,
                            )
                            check(ok) {
                                "Visual embedding failed on video frame ${frameIndex + 1}"
                            }
                            indexed++
                        }
                        MediaIngestionResult(name, format, indexed, timestamps.size)
                    } finally {
                        retriever.release()
                    }
                }
                MediaImportFormat.PDF -> {
                    val fd = resolver.openFileDescriptor(uri, "r")
                        ?: error("Unable to open PDF file")
                    fd.use { descriptor ->
                        val size = descriptor.statSize
                        require(size <= 64L * 1024 * 1024 || size < 0) {
                            "PDF is larger than 64 MiB"
                        }
                        PdfRenderer(descriptor).use { renderer ->
                            val total = renderer.pageCount
                            require(MediaRagImportRules.canIndexPages(total)) {
                                "PDF must have 1–${MediaRagImportRules.MAX_PDF_PAGES} pages; split longer documents"
                            }
                            var indexed = 0
                            for (pageIndex in 0 until total) {
                                currentCoroutineContext().ensureActive()
                                val png = renderer.openPage(pageIndex).use { page ->
                                    // Bound pixel memory even for enormous technical drawings.
                                    val scale = minOf(1.0, 1024.0 / max(page.width, page.height))
                                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    try {
                                        Canvas(bitmap).drawColor(Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        ByteArrayOutputStream().use { output ->
                                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                                "Could not rasterize page ${pageIndex + 1}"
                                            }
                                            output.toByteArray()
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                                val ok = repository.indexMedia(
                                    id = MediaRagImportRules.vectorId(opaqueSource, pageIndex),
                                    bytes = png,
                                    kind = MediaEmbeddingKind.IMAGE,
                                    metadata = buildJsonObject {
                                        put("text", "$metadataName · visual page ${pageIndex + 1}")
                                        put("sourceName", metadataName)
                                        put("sourceUri", uri.toString())
                                        put("page", pageIndex + 1)
                                        put("mediaType", "pdf")
                                    },
                                    expectedSpaceId = variant.spaceId,
                                )
                                check(ok) {
                                    "Visual embedding failed on PDF page ${pageIndex + 1}; $indexed of $total indexed"
                                }
                                indexed++
                            }
                            MediaIngestionResult(name, format, indexed, total)
                        }
                    }
                }
            }
        }

    private fun readLimited(input: InputStream, maximum: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maximum) { "Media file exceeds ${maximum / (1024 * 1024)} MiB limit" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
}
