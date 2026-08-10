package me.rerere.rikkahub.ui.pages.setting.components

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager.ModelKind

class QwenSemanticModelDownloadTest {
    @Test
    fun downloadUrlsUseTheExpectedRepositoryAndFilename() {
        val urls = QwenSemanticModelManager.downloadUrls(ModelKind.Embedder)

        assertEquals(
            QwenSemanticModelManager.requiredFiles(ModelKind.Embedder),
            urls.map { it.second },
        )
        assertTrue(urls.all { it.first.startsWith("https://huggingface.co/") })
        assertTrue(urls.all { it.first.contains("/resolve/main/") })
    }

    @Test
    fun failedBundleDoesNotRemoveExistingReadyFiles() {
        val root = Files.createTempDirectory("qwen-preserve").toFile()
        val target = root.resolve("embedder")
        target.mkdirs()
        target.resolve("existing.marker").writeText("keep")
        val staging = root.resolve("staging")
        staging.mkdirs()

        try {
            val result = runCatching {
                QwenSemanticModelManager.promoteValidated(staging, target, ModelKind.Embedder)
            }
            assertTrue(result.isFailure)
            assertEquals("keep", target.resolve("existing.marker").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun successfulBundlePromotesOnlyAfterAllFilesValidate() {
        val root = Files.createTempDirectory("qwen-promote").toFile()
        val target = root.resolve("embedder")
        val staging = root.resolve("staging")
        staging.mkdirs()
        QwenSemanticModelManager.requiredFiles(ModelKind.Embedder).forEach { fileName ->
            staging.resolve(fileName).writeText("model")
        }

        try {
            val promoted = QwenSemanticModelManager.promoteValidated(
                staging,
                target,
                ModelKind.Embedder,
            )
            assertEquals(target, promoted)
            assertTrue(target.isDirectory)
            assertTrue(!staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
