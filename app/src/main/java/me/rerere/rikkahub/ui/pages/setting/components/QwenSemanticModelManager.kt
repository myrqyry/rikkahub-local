package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient
import okhttp3.Request

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
     * files are immutable per-commit (`/resolve/<revision>/...`, the revision is pinned
     * to the resolved model-info `sha` at install time so the four files can never mix
     * revisions), come with server-declared sizes and SHA-256 hashes (validated after
     * download), and the org is the upstream publisher of the runtimes this app
     * consumes. All other model installation routes through the source page.
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

        val manifest = readManifest(directory)
        val missing = requiredFiles(kind).filter { name ->
            val file = File(directory, name)
            val expected = manifest?.files?.get(name)
            when {
                !file.isFile || !file.canRead() -> true
                // A recorded size/hash from the server catches truncated or corrupted
                // downloads; without a manifest (legacy/manual installs) fall back to a
                // non-empty check.
                expected != null -> {
                    val sizeOk = file.length() == expected.size
                    val hashOk = expected.sha256 == null ||
                        sha256Hex(file) == expected.sha256
                    !sizeOk || !hashOk
                }
                else -> file.length() <= 0L
            }
        }
        return if (missing.isEmpty()) {
            ModelStatus.Ready(directory)
        } else {
            ModelStatus.Incomplete(missing)
        }
    }

    internal data class ExpectedFile(
        val size: Long,
        val sha256: String?,
    )

    internal data class ManifestInfo(
        val revision: String?,
        val files: Map<String, ExpectedFile>,
    )

    private fun readManifest(directory: File): ManifestInfo? =
        runCatching {
            val root = Json.parseToJsonElement(File(directory, MANIFEST_FILE).readText())
                .jsonObject
            val revision = root["revision"]?.jsonPrimitive?.content
            val files = root["files"]!!.jsonObject.mapValues { (_, value) ->
                val obj = value as? JsonObject
                val size = obj?.get("size")?.jsonPrimitive?.long
                    ?: value.jsonPrimitive.long
                val sha256 = obj?.get("sha256")?.jsonPrimitive?.content
                ExpectedFile(size = size, sha256 = sha256)
            }
            ManifestInfo(revision, files)
        }.getOrNull()

    private fun writeManifest(
        directory: File,
        revision: String,
        files: Map<String, ExpectedFile>,
    ) {
        val filesJson = JsonObject(
            files.mapValues { (_, expected) ->
                buildJsonObject {
                    put("size", expected.size)
                    if (expected.sha256 != null) put("sha256", expected.sha256)
                }
            }
        )
        File(directory, MANIFEST_FILE).writeText(
            Json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("revision", revision)
                    put("files", filesJson)
                },
            )
        )
    }

    fun downloadUrls(kind: ModelKind): List<Pair<String, String>> =
        downloadUrls(kind, revision = "main")

    fun downloadUrls(kind: ModelKind, revision: String): List<Pair<String, String>> {
        val repository = repositoryUrl(kind)
        return requiredFiles(kind).map { fileName ->
            "$repository/resolve/$revision/$fileName?download=true" to fileName
        }
    }

    internal fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Parse the HF `/api/models/<repo>/tree/<revision>` response into expected file
     * metadata. Each entry: `{"path": ..., "size": ..., "lfs": {"oid": "sha256:<hex>", ...}}`.
     * Entries without `lfs.oid` (non-LFS files) carry `sha256 = null` — size only.
     */
    internal fun parseFileMetadata(
        jsonText: String,
        required: List<String>,
    ): Map<String, ExpectedFile> =
        Json.parseToJsonElement(jsonText).jsonArray.mapNotNull { element ->
            val objectValue = element.jsonObject
            val path = objectValue["path"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            if (path !in required) return@mapNotNull null
            val size = objectValue["size"]?.jsonPrimitive?.long ?: 0L
            val oid = objectValue["lfs"]?.jsonObject?.get("oid")?.jsonPrimitive?.content
            val sha256 = oid?.takeIf { it.startsWith("sha256:") }
                ?.substringAfter("sha256:")
            path to ExpectedFile(size = size, sha256 = sha256)
        }.toMap()

    private fun repositoryUrl(kind: ModelKind): String = when (kind) {
        ModelKind.Embedder -> EMBEDDER_REPOSITORY
        ModelKind.Reranker -> RERANKER_REPOSITORY
    }

    private fun apiUrl(kind: ModelKind, suffix: String): String {
        val repoId = repositoryUrl(kind).substringAfter("https://huggingface.co/")
        return "https://huggingface.co/api/models/$repoId$suffix"
    }

    /** Resolve the pinned revision (SHA) of the repository's default branch. */
    private suspend fun resolveRevision(client: OkHttpClient, kind: ModelKind): String {
        val url = apiUrl(kind, "")
        val body = client.newCall(Request.Builder().url(url).build())
            .execute().use { response ->
                if (!response.isSuccessful) {
                    error("Failed to resolve model revision: HTTP ${response.code}")
                }
                response.body?.string() ?: error("Empty revision response")
            }
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content
        }.getOrNull() ?: error("Model revision response missing sha")
    }

    private suspend fun fetchFileMetadata(
        client: OkHttpClient,
        kind: ModelKind,
        revision: String,
    ): Map<String, ExpectedFile> {
        val url = apiUrl(kind, "/tree/$revision")
        val body = client.newCall(Request.Builder().url(url).build())
            .execute().use { response ->
                if (!response.isSuccessful) {
                    error("Failed to fetch model file metadata: HTTP ${response.code}")
                }
                response.body?.string() ?: error("Empty tree response")
            }
        return parseFileMetadata(body, requiredFiles(kind))
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
                revision = "",
                files = requiredFiles(kind).associateWith { fileName ->
                    ExpectedFile(destination.resolve(fileName).length(), sha256 = null)
                },
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
            val revision = resolveRevision(client, kind)
            val metadata = fetchFileMetadata(client, kind, revision)
            downloadUrls(kind, revision).forEachIndexed { index, (url, fileName) ->
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
                            val expectedSha = metadata[fileName]?.sha256
                            if (expectedSha != null && sha256Hex(file) != expectedSha) {
                                file.delete()
                                throw IllegalStateException(
                                    "Downloaded $fileName failed SHA-256 verification"
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
                revision = revision,
                files = requiredFiles(kind).associateWith { fileName ->
                    ExpectedFile(
                        size = expectedSizes[fileName] ?: destination.resolve(fileName).length(),
                        sha256 = metadata[fileName]?.sha256,
                    )
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
