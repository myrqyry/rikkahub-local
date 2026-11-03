package me.rerere.rikkahub.ui.pages.models.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingGemma2CatalogStateTest {
    @Test fun selectedModelCannotShowReadyWhenFileIsMissing() {
        assertEquals(
            EmbeddingGemma2CatalogState.SELECTED_MISSING,
            embeddingGemma2CatalogState(selected = true, fileExists = false, expectedSize = false),
        )
    }

    @Test fun selectedModelCannotShowReadyWhenFileIsIncomplete() {
        assertEquals(
            EmbeddingGemma2CatalogState.SELECTED_INCOMPLETE,
            embeddingGemma2CatalogState(selected = true, fileExists = true, expectedSize = false),
        )
    }

    @Test fun completeSelectedModelShowsSelectionAndRecoveryIsNotNeeded() {
        assertEquals(
            EmbeddingGemma2CatalogState.SELECTED_AVAILABLE,
            embeddingGemma2CatalogState(selected = true, fileExists = true, expectedSize = true),
        )
    }

    @Test fun unselectedVariantsExposeDownloadOrSelectionAccordingToFileStatus() {
        assertEquals(
            EmbeddingGemma2CatalogState.NOT_DOWNLOADED,
            embeddingGemma2CatalogState(selected = false, fileExists = false, expectedSize = false),
        )
        assertEquals(
            EmbeddingGemma2CatalogState.INCOMPLETE,
            embeddingGemma2CatalogState(selected = false, fileExists = true, expectedSize = false),
        )
        assertEquals(
            EmbeddingGemma2CatalogState.AVAILABLE,
            embeddingGemma2CatalogState(selected = false, fileExists = true, expectedSize = true),
        )
    }
}
