package me.rerere.rikkahub.ui.pages.setting.components

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager.ModelKind
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager.ModelStatus

class QwenSemanticModelManagerTest {
    @Test
    fun emptyPathIsNotInstalled() {
        assertTrue(
            QwenSemanticModelManager.validate(java.io.File(""), ModelKind.Embedder)
                is ModelStatus.NotInstalled
        )
    }

    @Test
    fun missingFilesAreReported() {
        val directory = Files.createTempDirectory("qwen-missing").toFile()
        try {
            val status = QwenSemanticModelManager.validate(directory, ModelKind.Embedder)
                as ModelStatus.Incomplete
            assertEquals(QwenSemanticModelManager.requiredFiles(ModelKind.Embedder), status.missingFiles)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun zeroLengthFileIsIncomplete() {
        val directory = Files.createTempDirectory("qwen-empty").toFile()
        try {
            QwenSemanticModelManager.requiredFiles(ModelKind.Embedder).forEach { directory.resolve(it).createNewFile() }
            val status = QwenSemanticModelManager.validate(directory, ModelKind.Embedder)
                as ModelStatus.Incomplete
            assertEquals(QwenSemanticModelManager.requiredFiles(ModelKind.Embedder), status.missingFiles)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun completeEmbedderBundleIsReady() {
        val directory = completeDirectory(ModelKind.Embedder)
        try {
            val status = QwenSemanticModelManager.validate(directory, ModelKind.Embedder)
                as ModelStatus.Ready
            assertEquals(directory, status.directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun completeRerankerBundleIsReady() {
        val directory = completeDirectory(ModelKind.Reranker)
        try {
            val status = QwenSemanticModelManager.validate(directory, ModelKind.Reranker)
                as ModelStatus.Ready
            assertEquals(directory, status.directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun truncatedBundleIsIncompleteAgainstManifest() {
        val directory = Files.createTempDirectory("qwen-truncated").toFile()
        try {
            val files = QwenSemanticModelManager.requiredFiles(ModelKind.Embedder)
            files.forEach { directory.resolve(it).writeText("model") }
            // Manifest records a larger size than the actual bytes → truncated.
            writeManifest(
                directory,
                files.associateWith { 1_048_576L },
            )

            val status = QwenSemanticModelManager.validate(directory, ModelKind.Embedder)
                as ModelStatus.Incomplete
            assertEquals(files, status.missingFiles)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun manifestMatchingSizesAreReady() {
        val directory = Files.createTempDirectory("qwen-sized").toFile()
        try {
            val files = QwenSemanticModelManager.requiredFiles(ModelKind.Embedder)
            files.forEach { directory.resolve(it).writeText("model") }
            val sizes = files.associateWith { directory.resolve(it).length() }
            writeManifest(directory, sizes)

            assertTrue(
                QwenSemanticModelManager.validate(directory, ModelKind.Embedder)
                    is ModelStatus.Ready
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeManifest(directory: java.io.File, sizes: Map<String, Long>) {
        val files = kotlinx.serialization.json.JsonObject(
            sizes.mapValues { (_, size) -> kotlinx.serialization.json.JsonPrimitive(size) }
        )
        directory.resolve("manifest.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.JsonObject(mapOf("files" to files)),
            )
        )
    }

    private fun completeDirectory(kind: ModelKind): java.io.File =
        Files.createTempDirectory("qwen-ready").toFile().also { directory ->
            QwenSemanticModelManager.requiredFiles(kind).forEach { fileName ->
                directory.resolve(fileName).writeText("model")
            }
        }
}
