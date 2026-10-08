package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Backend
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Model
import me.rerere.rikkahub.data.rag.RagEmbeddingSource
import me.rerere.rikkahub.data.rag.DEFAULT_EMBEDDING_MODEL
import me.rerere.rikkahub.data.rag.resolveRagEmbeddingSource
import me.rerere.rikkahub.data.datastore.Settings
import java.io.File
import org.junit.Assert.assertEquals
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

    @Test
    fun explicitSelectionDoesNotSilentlyUseQwenOrProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "models/embedder")
        val selected = Settings(ragEmbeddingModel = EmbeddingGemma2Model.MODEL_ID)
        assertTrue(resolveRagEmbeddingSource(selected, dir, localReady = false) ===
            RagEmbeddingSource.LocalEmbeddingGemma2)
        assertTrue(resolveRagEmbeddingSource(selected, dir, localReady = true) ===
            RagEmbeddingSource.LocalEmbeddingGemma2)
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
