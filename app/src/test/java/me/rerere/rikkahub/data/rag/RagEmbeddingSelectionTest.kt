package me.rerere.rikkahub.data.rag

import java.io.File
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.search.SearchServiceOptions
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager
import me.rerere.rikkahub.ui.pages.setting.components.updateQwenModelDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagEmbeddingSelectionTest {
    private val embedderDir = File("/models/embedder")

    @Test
    fun freshSettingsWithLocalBundleReadySelectsLocalQwen() {
        val source = resolveRagEmbeddingSource(Settings(), embedderDir, localReady = true)
        assertEquals(RagEmbeddingSource.LocalQwen(embedderDir), source)
    }

    @Test
    fun noLocalAndNoMatchingProviderModelResolvesToNull() {
        assertTrue(resolveRagEmbeddingSource(Settings(), embedderDir, localReady = false) == null)
    }

    @Test
    fun nonDefaultAssignmentDoesNotSelectLocalQwenEvenWhenReady() {
        val settings = Settings().copy(ragEmbeddingModel = "custom-embedding")
        assertTrue(resolveRagEmbeddingSource(settings, embedderDir, localReady = true) == null)
    }

    @Test
    fun setupAppendsQwenOptionsWhenNoneExists() {
        val updated = updateQwenModelDirectory(
            Settings(),
            QwenSemanticModelManager.ModelKind.Embedder,
            embedderDir,
        )
        val option = updated.searchServices
            .filterIsInstance<SearchServiceOptions.QwenEmbedderOptions>()
            .firstOrNull()
        assertTrue("expected a QwenEmbedderOptions to be appended", option != null)
        assertEquals(embedderDir.absolutePath, option!!.modelDir)
    }
}
