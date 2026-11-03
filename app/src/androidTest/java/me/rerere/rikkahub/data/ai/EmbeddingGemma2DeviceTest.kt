package me.rerere.rikkahub.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Backend
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Model
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Variant
import me.rerere.rikkahub.data.rag.RagEmbeddingSource
import me.rerere.rikkahub.data.rag.DEFAULT_EMBEDDING_MODEL
import me.rerere.rikkahub.data.rag.resolveRagEmbeddingSource
import me.rerere.rikkahub.data.datastore.Settings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real-model tests, invoked manually by adb, never as an automatic install/uninstall task. */
@RunWith(AndroidJUnit4::class)
class EmbeddingGemma2DeviceTest {
    @Test
    fun modelFileMatchesOfficialSha256() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(EmbeddingGemma2Model.verify(EmbeddingGemma2Model.file(context)))
    }

    @Test
    fun producesRealFiniteNormalizedTextVectors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val embedder = EmbeddingGemma2Backend(context)
        val query = withTimeout(90_000L) {
            embedder.embedQuery("Which animal purrs and catches mice?")
        } ?: error("EmbeddingGemma query returned no vector")
        val cat = withTimeout(90_000L) {
            embedder.embed("Cats purr and catch small mice.")
        } ?: error("EmbeddingGemma cat document returned no vector")
        val physics = withTimeout(90_000L) {
            embedder.embed("How electrical resistivity changes with copper temperature.")
        } ?: error("EmbeddingGemma physics document returned no vector")
        for (result in listOf(query, cat, physics)) {
            assertEquals(EmbeddingGemma2Model.DIMENSION, result.embedding.size)
            assertTrue(result.embedding.all { it.isFinite() })
            val norm = sqrt(result.embedding.sumOf { (it * it).toDouble() })
            assertTrue("Vector was not L2-normalized: $norm", norm in 0.95..1.05)
        }
        fun score(a: FloatArray, b: FloatArray): Double =
            a.indices.sumOf { (a[it] * b[it]).toDouble() }
        val catScore = score(query.embedding, cat.embedding)
        val physicsScore = score(query.embedding, physics.embedding)
        Log.i("EmbeddingGemma2Acceptance", "cat=$catScore physics=$physicsScore")
        assertTrue("Relevant document was not ranked higher", catScore > physicsScore)
    }

    /**
     * Actual Pixel/LiteRT vision acceptance. Uses generated in-memory PNGs,
     * never the user's photos, database, settings or chat history.
     * Requires the pinned 440M bundle to have been explicitly downloaded.
     */
    @Test
    fun producesRealFiniteVisionEmbeddingsFromPng() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val variant = EmbeddingGemma2Variant.VISION
        assertTrue("440M bundle must be present and checksum-valid", variant.verify(variant.file(context)))
        val embedder = EmbeddingGemma2Backend(context, variant)

        fun solidSquarePng(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            return try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply { this.color = color }
                canvas.drawRect(24f, 24f, 104f, 104f, paint)
                ByteArrayOutputStream().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        val red = withTimeout(120_000L) {
            embedder.embedImage(solidSquarePng(Color.RED))
        } ?: error("440M native red-square embedding returned no vector")
        val blue = withTimeout(120_000L) {
            embedder.embedImage(solidSquarePng(Color.BLUE))
        } ?: error("440M native blue-square embedding returned no vector")
        val query = withTimeout(120_000L) {
            embedder.embedQuery("an image with a bright red square")
        } ?: error("440M native query returned no vector")

        for (result in listOf(red, blue, query)) {
            assertEquals(variant.dimension, result.embedding.size)
            assertTrue(result.embedding.all { it.isFinite() })
            val norm = sqrt(result.embedding.sumOf { (it * it).toDouble() })
            assertTrue("Vision/query vector was not normalized: $norm", norm in 0.95..1.05)
        }

        val different = red.embedding.indices.any {
            kotlin.math.abs(red.embedding[it] - blue.embedding[it]) > 0.00001f
        }
        assertTrue("Different PNG inputs produced identical embeddings", different)
        Log.i("EmbeddingGemma2Acceptance", "440M vision real vectors: red, blue and text query, dims=768, normalized")
        Unit // AndroidJUnit4 requires a void test method, not Log.i's Int result.
    }

    /**
     * Full bundle acceptance: synthesize 48 kHz stereo PCM, normalize to the
     * model's 16 kHz mono input, and execute the real native audio encoder.
     * Generates no sound and accesses no microphone or user recordings.
     */
    @Test
    fun producesRealFiniteAudioEmbeddingsFromWav() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val variant = EmbeddingGemma2Variant.FULL
        assertTrue("740M bundle must be present and checksum-valid", variant.verify(variant.file(context)))
        val embedder = EmbeddingGemma2Backend(context, variant)

        fun syntheticWav(frequencyHz: Double): ByteArray {
            val sampleRate = 48_000
            val frames = sampleRate
            val dataBytes = frames * 4 // PCM16 stereo
            val out = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            out.put("RIFF".toByteArray(Charsets.US_ASCII))
            out.putInt(36 + dataBytes)
            out.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            out.putInt(16)
            out.putShort(1.toShort())
            out.putShort(2.toShort())
            out.putInt(sampleRate)
            out.putInt(sampleRate * 4)
            out.putShort(4.toShort())
            out.putShort(16.toShort())
            out.put("data".toByteArray(Charsets.US_ASCII))
            out.putInt(dataBytes)
            repeat(frames) { position ->
                val wave = kotlin.math.sin(2.0 * Math.PI * frequencyHz * position / sampleRate)
                val sample = (wave * 16_000).toInt().toShort()
                out.putShort(sample)
                out.putShort(sample)
            }
            return out.array()
        }

        val low = withTimeout(120_000L) {
            embedder.embedAudio(syntheticWav(440.0))
        } ?: error("740M native 440Hz audio returned no vector")
        val high = withTimeout(120_000L) {
            embedder.embedAudio(syntheticWav(880.0))
        } ?: error("740M native 880Hz audio returned no vector")
        val query = withTimeout(120_000L) {
            embedder.embedQuery("a high-pitched electronic beep")
        } ?: error("740M native text query returned no vector")
        for (result in listOf(low, high, query)) {
            assertEquals(variant.dimension, result.embedding.size)
            assertTrue(result.embedding.all { it.isFinite() })
            val norm = sqrt(result.embedding.sumOf { (it * it).toDouble() })
            assertTrue("Audio/query vector was not normalized: $norm", norm in 0.95..1.05)
        }
        val different = low.embedding.indices.any {
            kotlin.math.abs(low.embedding[it] - high.embedding[it]) > 0.00001f
        }
        assertTrue("Different WAV tones produced identical embeddings", different)
        Log.i("EmbeddingGemma2Acceptance", "740M native real WAV audio vectors: 440Hz, 880Hz and text query")
        Unit
    }

    @Test
    fun explicitSelectionDoesNotSilentlyUseQwenOrProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "models/embedder")
        val selected = Settings(ragEmbeddingModel = EmbeddingGemma2Model.MODEL_ID)
        assertEquals(
            RagEmbeddingSource.LocalEmbeddingGemma2(EmbeddingGemma2Variant.TEXT),
            resolveRagEmbeddingSource(selected, dir, localReady = false),
        )
        assertEquals(
            RagEmbeddingSource.LocalEmbeddingGemma2(EmbeddingGemma2Variant.TEXT),
            resolveRagEmbeddingSource(selected, dir, localReady = true),
        )
        val default = Settings(ragEmbeddingModel = DEFAULT_EMBEDDING_MODEL)
        assertTrue(resolveRagEmbeddingSource(default, dir, localReady = true) is
            RagEmbeddingSource.LocalQwen)
    }

    @Test
    fun enforcesAsymmetricRetrievalInstructions() {
        assertEquals(
            "task: search result | query: a cat",
            EmbeddingGemma2Model.searchQuery("a cat")
        )
        assertEquals(
            "title: none | text: something",
            EmbeddingGemma2Model.document("something")
        )
        assertEquals(
            "title: photo.jpg | text: a kitten",
            EmbeddingGemma2Model.document("a kitten", "photo.jpg")
        )
    }
}
