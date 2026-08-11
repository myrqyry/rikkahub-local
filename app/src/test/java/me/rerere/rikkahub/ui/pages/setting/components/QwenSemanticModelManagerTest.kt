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

    @Test
    fun parseFileMetadataExtractsPinnedSizesAndSha256() {
        val json = """
            [
              {"path": "qwen3emb_gpu_fp16.tflite", "size": 12, "lfs": {"oid": "sha256:abc123", "size": 12}},
              {"path": "embeddings_fp16.bin", "size": 34, "lfs": {"oid": "sha256:def456", "size": 34}},
              {"path": "vocab.json", "size": 56},
              {"path": "unrelated.bin", "size": 999, "lfs": {"oid": "sha256:zzz", "size": 999}}
            ]
        """.trimIndent()

        val parsed = QwenSemanticModelManager.parseFileMetadata(
            json,
            QwenSemanticModelManager.requiredFiles(ModelKind.Embedder),
        )

        assertEquals(
            QwenSemanticModelManager.ExpectedFile(12, "abc123"),
            parsed["qwen3emb_gpu_fp16.tflite"],
        )
        assertEquals(
            QwenSemanticModelManager.ExpectedFile(34, "def456"),
            parsed["embeddings_fp16.bin"],
        )
        assertEquals(
            QwenSemanticModelManager.ExpectedFile(56, null),
            parsed["vocab.json"],
        )
        assertEquals(3, parsed.size)
    }

    @Test
    fun sha256HexMatchesKnownDigest() {
        val file = Files.createTempDirectory("qwen-sha").toFile()
            .resolve("sample.bin")
        try {
            file.writeText("hello world")
            assertEquals(
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                QwenSemanticModelManager.sha256Hex(file),
            )
        } finally {
            file.delete()
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
