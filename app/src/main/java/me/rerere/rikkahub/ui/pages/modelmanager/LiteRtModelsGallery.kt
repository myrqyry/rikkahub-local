package me.rerere.rikkahub.ui.pages.modelmanager

import android.content.Context
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of the model table in github.com/john-rocky/LiteRT-Models.
 *
 * A catalog listing is NOT a claim that this RikkaHub build executes that model.
 * .litertlm can be routed through LiteRT-LM; CompiledModel .tflite graphs need
 * task-specific adapters, pre/post-processing and verification before use.
 */
data class LiteRtGalleryModel(
    val name: String,
    val task: String,
    val device: String,
    val reportedPerformance: String,
    val sourceUrl: String,
) {
    val slug: String = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(72)
}

data class LiteRtGalleryAsset(
    val path: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
    /** Hugging Face LFS SHA-256 when provided. Git SHA-1 is deliberately not accepted. */
    val sha256: String?,
) {
    val extension: String get() = path.substringAfterLast('.', "").lowercase()
    val isRunnableLiteRtLm: Boolean get() = extension == "litertlm"
}

object LiteRtModelsGallery {
    const val UPSTREAM_URL = "https://github.com/john-rocky/LiteRT-Models"
    const val SNAPSHOT_COMMIT = "15c41a55bc68caea11b684c10bfe081cff411c08"
    private const val ASSET = "litert_models_gallery.tsv"
    private val assetExtensions = setOf(
        "litertlm", "tflite", "onnx", "gguf", "bin", "safetensors",
        "json", "txt", "spiece", "model", "spm", "vocab", "npy", "zip", "pte",
    )

    fun load(context: Context): List<LiteRtGalleryModel> =
        parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

    fun parse(tsv: String): List<LiteRtGalleryModel> =
        tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .map { line ->
                val fields = line.split('\t')
                require(fields.size == 5) { "Invalid LiteRT catalog snapshot row" }
                val (name, task, device, performance, url) = fields
                require(name.isNotBlank() && url.startsWith("https://"))
                LiteRtGalleryModel(name, task, device, performance, url)
            }.toList()

    /**
     * Only GitHub release assets from the source repository and Hugging Face
     * repository contents are offered as downloads. Script-only entries are still
     * listed, with their upstream source link and no fake artifact.
     *
     * HF repository files are resolved against a single commit SHA per selection
     * (not a mutable /resolve/main URL); the per-file LFS hash is also captured.
     */
    suspend fun discover(
        client: OkHttpClient,
        entry: LiteRtGalleryModel,
    ): List<LiteRtGalleryAsset> = withContext(Dispatchers.IO) {
        val source = URI(entry.sourceUrl)
        val host = source.host?.lowercase()
        val parts = source.path.trim('/').split('/')
        when {
            host == "github.com" &&
                parts.size >= 6 &&
                parts.take(4) == listOf("john-rocky", "LiteRT-Models", "releases", "download") -> {
                val leaf = parts.last()
                if (supported(leaf)) listOf(LiteRtGalleryAsset(leaf, entry.sourceUrl, null, null))
                else emptyList()
            }
            host == "huggingface.co" && parts.size >= 5 &&
                parts[2] == "resolve" -> {
                val leaf = parts.drop(4).joinToString("/")
                if (supported(leaf)) listOf(
                    LiteRtGalleryAsset(leaf, entry.sourceUrl, null, null)
                ) else emptyList()
            }
            host == "huggingface.co" && parts.size == 2 -> {
                val owner = parts[0]
                val repo = parts[1]
                val info = readJson(client, "https://huggingface.co/api/models/$owner/$repo") as JSONObject
                val revision = info.getString("sha")
                require(Regex("[0-9a-f]{40}").matches(revision)) { "Unrecognized Hugging Face revision" }
                val files = readJson(
                    client,
                    "https://huggingface.co/api/models/$owner/$repo/tree/$revision?recursive=true&expand=true",
                ) as JSONArray
                buildList {
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        if (file.optString("type") != "file") continue
                        val path = file.optString("path")
                        if (!supported(path) || !safeRelativePath(path)) continue
                        val lfsHash = file.optJSONObject("lfs")?.optString("oid")
                            ?.takeIf { Regex("[0-9a-fA-F]{64}").matches(it) }
                        val bytes = file.optLong("size", -1L).takeIf { it > 0 }
                        val url = "https://huggingface.co/$owner/$repo/resolve/$revision/" +
                            path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                        add(LiteRtGalleryAsset(path, url, bytes, lfsHash))
                    }
                }.sortedWith(compareBy({ !it.isRunnableLiteRtLm }, { it.path }))
            }
            else -> emptyList()
        }
    }

    fun targetFile(context: Context, model: LiteRtGalleryModel, asset: LiteRtGalleryAsset): File {
        require(safeRelativePath(asset.path) && supported(asset.path))
        val pathDigest = MessageDigest.getInstance("SHA-256")
            .digest(asset.path.toByteArray(Charsets.UTF_8))
            .take(5).joinToString("") { "%02x".format(it) }
        val basename = asset.path.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val slug = model.slug.ifBlank { "unnamed" }
        return if (asset.isRunnableLiteRtLm) {
            File(context.filesDir, "local-models/litert/gallery-$slug-$pathDigest-$basename")
        } else {
            // Store unsupported runtime assets separately; don't register .tflite/.onnx
            // as chat models or overwrite the existing local model directories.
            val root = context.getExternalFilesDir(null) ?: context.filesDir
            File(root, "local-models/litert-gallery/$slug/$pathDigest-$basename")
        }
    }

    fun verify(file: File, asset: LiteRtGalleryAsset): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (asset.sizeBytes != null && file.length() != asset.sizeBytes) return false
        if (asset.sha256 != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val block = ByteArray(128 * 1024)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    digest.update(block, 0, count)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(asset.sha256, ignoreCase = true)) return false
        }
        return true
    }

    private fun supported(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in assetExtensions

    private fun safeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun readJson(client: OkHttpClient, url: String): Any {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Catalog files unavailable (HTTP ${response.code})" }
            val body = response.body.string()
            return if (body.trimStart().startsWith('[')) JSONArray(body) else JSONObject(body)
        }
    }
}
