package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val missing = requiredFiles(kind).filter { name ->
            val file = File(directory, name)
            !file.isFile || !file.canRead() || file.length() <= 0L
        }
        return if (missing.isEmpty()) {
            ModelStatus.Ready(directory)
        } else {
            ModelStatus.Incomplete(missing)
        }
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
        destination.deleteRecursively()
        destination.mkdirs()
        try {
            downloadUrls(kind).forEachIndexed { index, (url, fileName) ->
                ModelInstall.download(client, url, destination.resolve(fileName)).collect { progress ->
                    when (progress) {
                        is ModelInstall.Progress.Started -> onProgress(0)
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
                        is ModelInstall.Progress.Done -> onFileDone(fileName, index, requiredFiles(kind).size)
                        is ModelInstall.Progress.Failed -> throw progress.cause
                    }
                }
            }
            promoteValidated(destination, modelDirectory(context, kind), kind)
        } catch (error: Throwable) {
            destination.deleteRecursively()
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
