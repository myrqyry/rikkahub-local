package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient

object QwenSemanticModelManager {
    enum class ModelKind {
        Embedder,
        Reranker,
    }

    sealed interface ModelStatus {
        data object NotInstalled : ModelStatus

        data class Incomplete(val missingFiles: List<String>) : ModelStatus

        data class Ready(val directory: File) : ModelStatus
    }

    /**
     * Install contract: unlike the catalog/source-page flow, these on-device LiteRT
     * bundles are downloaded directly from the official `litert-community` Hugging Face
     * organization. This is an explicit exception to the model-import contract — the
     * files are immutable per-commit (`/resolve/...`), come with server-declared sizes
     * (validated after download), and the org is the upstream publisher of the runtimes
     * this app consumes. All other model installation routes through the source page.
     */
    private const val MANIFEST_FILE = "manifest.json"

    private const val EMBEDDER_REPOSITORY =
        "https://huggingface.co/litert-community/Qwen3-Embedding-0.6B-LiteRT"
    private const val RERANKER_REPOSITORY =
        "https://huggingface.co/litert-community/Qwen3-Reranker-0.6B-LiteRT"

    private val embedderFiles = listOf(
        "qwen3emb_gpu_fp16.tflite",
        "embeddings_fp16.bin",
        "vocab.json",
        "merges.txt",
    )

    private val rerankerFiles = listOf(
        "qwen3rerank_gpu_fp16.tflite",
        "embeddings_fp16.bin",
        "vocab.json",
        "merges.txt",
    )

    fun requiredFiles(kind: ModelKind): List<String> = when (kind) {
        ModelKind.Embedder -> embedderFiles
        ModelKind.Reranker -> rerankerFiles
    }

    fun modelDirectory(context: Context, kind: ModelKind): File =
        File(context.filesDir, "models/${kind.directoryName}")

    fun validate(directory: File, kind: ModelKind): ModelStatus {
        if (!directory.isDirectory) return ModelStatus.NotInstalled

        val expected = readManifest(directory)
        val missing = requiredFiles(kind).filter { name ->
            val file = File(directory, name)
            val expectedSize = expected?.get(name)
            when {
                !file.isFile || !file.canRead() -> true
                // A recorded size from the server catches truncated downloads; without a
                // manifest (legacy/manual installs) fall back to a non-empty check.
                expectedSize != null -> file.length() != expectedSize
                else -> file.length() <= 0L
            }
        }
        return if (missing.isEmpty()) {
            ModelStatus.Ready(directory)
        } else {
            ModelStatus.Incomplete(missing)
        }
    }

    private fun readManifest(directory: File): Map<String, Long>? =
        runCatching {
            val files = Json.parseToJsonElement(File(directory, MANIFEST_FILE).readText())
                .jsonObject["files"]!!.jsonObject
            files.mapValues { (_, value) -> value.jsonPrimitive.long }
        }.getOrNull()

    private fun writeManifest(directory: File, sizes: Map<String, Long>) {
        val files = JsonObject(
            sizes.mapValues { (_, size) -> JsonPrimitive(size) }
        )
        File(directory, MANIFEST_FILE).writeText(
            Json.encodeToString(
                JsonObject.serializer(),
                JsonObject(mapOf("files" to files)),
            )
        )
    }

    fun downloadUrls(kind: ModelKind): List<Pair<String, String>> {
        val repository = when (kind) {
            ModelKind.Embedder -> EMBEDDER_REPOSITORY
            ModelKind.Reranker -> RERANKER_REPOSITORY
        }
        return requiredFiles(kind).map { fileName ->
            "$repository/resolve/main/$fileName?download=true" to fileName
        }
    }

    suspend fun importBundleFromTree(
        context: Context,
        kind: ModelKind,
        treeUri: Uri,
    ): File = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("The selected location is not a folder")
        val sourceFiles = requiredFiles(kind).associateWith { fileName ->
            root.findFile(fileName)
                ?: error("Missing required file: $fileName")
        }
        val destination = stagingDirectory(context, kind)
        destination.deleteRecursively()
        destination.mkdirs()
        try {
            sourceFiles.forEach { (fileName, document) ->
                val source = context.contentResolver.openInputStream(document.uri)
                    ?: error("Could not read $fileName")
                source.use { input ->
                    destination.resolve(fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            writeManifest(
                destination,
                requiredFiles(kind).associateWith { destination.resolve(it).length() },
            )
            promoteValidated(destination, modelDirectory(context, kind), kind)
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        }
    }

    suspend fun downloadBundle(
        context: Context,
        client: OkHttpClient,
        kind: ModelKind,
        onFileDone: (name: String, index: Int, count: Int) -> Unit = { _, _, _ -> },
        onProgress: (percent: Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val destination = stagingDirectory(context, kind)
        // Keep the staging directory across attempts: ModelInstall.download resumes from
        // surviving `.partial` files via HTTP Range, so an interrupted download continues
        // instead of restarting from zero.
        destination.mkdirs()
        val expectedSizes = mutableMapOf<String, Long>()
        try {
            downloadUrls(kind).forEachIndexed { index, (url, fileName) ->
                ModelInstall.download(client, url, destination.resolve(fileName)).collect { progress ->
                    when (progress) {
                        is ModelInstall.Progress.Started -> {
                            progress.totalBytes?.let { expectedSizes[fileName] = it }
                            onProgress(0)
                        }
                        is ModelInstall.Progress.Tick -> {
                            val total = progress.totalBytes
                            onProgress(
                                if (total != null && total > 0L) {
                                    (progress.bytesRead * 100L / total).toInt()
                                } else {
                                    0
                                }
                            )
                        }
                        is ModelInstall.Progress.Done -> {
                            val file = destination.resolve(fileName)
                            val declared = expectedSizes[fileName]
                            if (declared != null && file.length() != declared) {
                                file.delete()
                                throw IllegalStateException(
                                    "Downloaded $fileName is truncated " +
                                        "(${file.length()} of $declared bytes)"
                                )
                            }
                            onFileDone(fileName, index, requiredFiles(kind).size)
                        }
                        is ModelInstall.Progress.Failed -> throw progress.cause
                    }
                }
            }
            writeManifest(
                destination,
                requiredFiles(kind).associateWith { fileName ->
                    expectedSizes[fileName] ?: destination.resolve(fileName).length()
                },
            )
            promoteValidated(destination, modelDirectory(context, kind), kind)
        } catch (error: Throwable) {
            // Keep any intact partial files so the next attempt resumes.
            throw error
        }
    }

    private fun stagingDirectory(context: Context, kind: ModelKind): File =
        File(context.filesDir, "models/.${kind.directoryName}-staging")

    internal fun promoteValidated(staging: File, target: File, kind: ModelKind): File {
        check(validate(staging, kind) is ModelStatus.Ready) {
            "Downloaded model is incomplete"
        }
        val backup = File(target.parentFile, ".${target.name}-previous")
        backup.deleteRecursively()
        val hadTarget = target.exists()
        if (hadTarget) check(target.renameTo(backup)) { "Could not stage existing model" }
        try {
            check(staging.renameTo(target)) { "Could not install model" }
        } catch (error: Throwable) {
            if (hadTarget) backup.renameTo(target)
            throw error
        }
        backup.deleteRecursively()
        return target
    }

    private val ModelKind.directoryName: String
        get() = when (this) {
            ModelKind.Embedder -> "embedder"
            ModelKind.Reranker -> "reranker"
        }
}
