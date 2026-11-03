package me.rerere.rikkahub.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Backend
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Variant
import me.rerere.rikkahub.data.rag.EmbeddingRepository
import me.rerere.rikkahub.data.rag.MediaEmbeddingKind
import me.rerere.rikkahub.data.rag.VectorDao
import me.rerere.rikkahub.data.rag.VectorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real PDF rendering → on-device vision embedding → local vector retrieval.
 * Never reads/writes the user's RAG database or changes selected models.
 * Generates a disposable PDF under the app's cache and deletes it afterward.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingGemma2PdfDeviceTest {
    private class MemoryDao : VectorDao {
        private val rows = linkedMapOf<String, VectorEntity>()
        override suspend fun getAll(): List<VectorEntity> = rows.values.toList()
        override suspend fun insert(entity: VectorEntity) { rows[entity.id] = entity }
        override suspend fun deleteById(id: String) { rows.remove(id) }
        override suspend fun deleteAll() { rows.clear() }
        override suspend fun count(): Int = rows.size
    }

    @Test
    fun rendersActualPdfAndFindsItsPageThroughNativeVisionSearch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val variant = EmbeddingGemma2Variant.VISION
        assertTrue("The pinned 440M model must already be downloaded", variant.verify(variant.file(context)))

        val pdfFile = File.createTempFile("meristem-pdf-device-smoke-", ".pdf", context.cacheDir)
        val pagePng = try {
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(640, 360, 1).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.drawText(
                    "Hello Meristem - visual PDF retrieval",
                    35f, 125f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 28f
                    },
                )
                doc.finishPage(page)
                pdfFile.outputStream().use { doc.writeTo(it) }
            } finally {
                doc.close()
            }

            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width, page.height, Bitmap.Config.ARGB_8888
                        )
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            ByteArrayOutputStream().use { out ->
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                                out.toByteArray()
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            assertTrue("Temporary PDF must be cleaned up", pdfFile.delete())
        }

        val backend = EmbeddingGemma2Backend(context, variant)
        val dao = MemoryDao()
        val repository = EmbeddingRepository({ backend }, dao, Json)
        val metadata = buildJsonObject {
            put("text", "Hello Meristem - visual PDF retrieval")
            put("mediaType", "pdf")
            put("page", 1)
        }
        val didIndex = withTimeout(120_000L) {
            repository.indexMedia(
                id = "device-test-visual-pdf-page-1",
                bytes = pagePng,
                kind = MediaEmbeddingKind.IMAGE,
                metadata = metadata,
                expectedSpaceId = variant.spaceId,
            )
        }
        assertTrue("Real PDF page failed to index", didIndex)
        assertEquals(1, dao.count())

        val result = withTimeout(120_000L) {
            repository.searchMedia("a PDF page saying Hello Meristem")
        }.single()
        assertEquals("device-test-visual-pdf-page-1", result.id)
        assertTrue(result.score.isFinite())
        assertEquals("pdf", result.metadata["mediaType"]?.jsonPrimitive?.content)
        assertEquals(1, result.metadata["page"]?.jsonPrimitive?.content?.toInt())
    }
}
