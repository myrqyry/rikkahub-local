package me.rerere.rikkahub.ui.pages.setting.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.search.SearchServiceOptions

class QwenSemanticModelSetupStateTest {
    @Test
    fun embedderReadyDoesNotMarkRerankerReady() {
        val embedder = SearchServiceOptions.QwenEmbedderOptions(documents = listOf("one"))
        val reranker = SearchServiceOptions.QwenRerankerOptions(documents = listOf("two"))
        val updated = updateQwenModelDirectory(
            Settings(searchServices = listOf(embedder, reranker)),
            QwenSemanticModelManager.ModelKind.Embedder,
            File("/models/embedder"),
        )

        val updatedEmbedder = updated.searchServices[0] as SearchServiceOptions.QwenEmbedderOptions
        val unchangedReranker = updated.searchServices[1] as SearchServiceOptions.QwenRerankerOptions
        assertEquals("/models/embedder", updatedEmbedder.modelDir)
        assertEquals("", unchangedReranker.modelDir)
        assertEquals(listOf("two"), unchangedReranker.documents)
    }

    @Test
    fun rerankerReadyDoesNotChangeEmbedderDirectory() {
        val embedder = SearchServiceOptions.QwenEmbedderOptions(modelDir = "/old/embedder")
        val reranker = SearchServiceOptions.QwenRerankerOptions()
        val updated = updateQwenModelDirectory(
            Settings(searchServices = listOf(embedder, reranker)),
            QwenSemanticModelManager.ModelKind.Reranker,
            File("/models/reranker"),
        )

        assertEquals(
            "/old/embedder",
            (updated.searchServices[0] as SearchServiceOptions.QwenEmbedderOptions).modelDir,
        )
        assertEquals(
            "/models/reranker",
            (updated.searchServices[1] as SearchServiceOptions.QwenRerankerOptions).modelDir,
        )
    }

    @Test
    fun incompleteModelKeepsExistingOptionDirectory() {
        val option = SearchServiceOptions.QwenEmbedderOptions(modelDir = "/existing")
        val settings = Settings(searchServices = listOf(option))
        val unchanged = settings

        assertTrue(unchanged.searchServices.single() === option)
        assertEquals("/existing", option.modelDir)
    }
}
