package me.rerere.rikkahub.skills.imports

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.skills.SkillUrlImporter
import me.rerere.rikkahub.skills.plugins.PluginManager
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class SkillUrlAdapter(private val importer: SkillUrlImporter) : ArtifactSourceAdapter {
    override fun supports(source: String): Boolean =
        importer.checkUrl(source) == null && GitHubSkillSource.parse(source) == null

    override suspend fun prepare(source: String): Result<ImportCandidate> = try {
        val prepared = importer.prepareFromUrl(source.githubSkillMarkdownUrl()).getOrThrow()
        Result.success(ImportCandidate(
            kind = ArtifactKind.SKILL,
            name = prepared.name,
            description = prepared.description,
            provenance = ArtifactProvenance(source, ArtifactSourceKind.URL, System.currentTimeMillis(), sha256(prepared.body)),
            payload = ImportPayload.SkillText(prepared.body),
        ))
    } catch (throwable: Throwable) {
        throwable.rethrowCancellation()
        Result.failure(throwable)
    }
}

/** Prepares a GitHub repository/tree skill without installing it. */
class GitHubSkillAdapter(
    private val importer: SkillUrlImporter,
    private val httpClient: OkHttpClient,
) : ArtifactSourceAdapter {
    override fun supports(source: String): Boolean = GitHubSkillSource.parse(source) != null

    override suspend fun prepare(source: String): Result<ImportCandidate> = withContext(Dispatchers.IO) {
        try {
            val ref = GitHubSkillSource.parse(source) ?: error("unsupported GitHub skill URL")
            val files = LinkedHashMap<String, String>()
            listFiles(ref, ref.path, ref.path, files, Budget())
            val skillBody = files["SKILL.md"] ?: error("No SKILL.md found in the directory")
            val prepared = importer.prepareFromText(skillBody, source).getOrThrow()
            files["SKILL.md"] = prepared.body
            Result.success(ImportCandidate(
                kind = ArtifactKind.SKILL,
                name = prepared.name,
                description = prepared.description,
                provenance = ArtifactProvenance(
                    source,
                    ArtifactSourceKind.GITHUB,
                    System.currentTimeMillis(),
                    sha256(files),
                ),
                payload = ImportPayload.SkillFiles(files),
            ))
        } catch (throwable: Throwable) {
            throwable.rethrowCancellation()
            Result.failure(throwable)
        }
    }

    private fun listFiles(
        ref: GitHubSkillSource,
        apiPath: String,
        basePath: String,
        files: MutableMap<String, String>,
        budget: Budget,
    ) {
        require(budget.depth++ < MAX_DEPTH) { "GitHub skill directory is too deep" }
        val url = contentsUrl(ref, apiPath)
        val body = request(url)
        val entries = JSONArray(body)
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            budget.nodes++
            require(budget.nodes <= MAX_NODES) { "GitHub skill catalog is too large" }
            val type = entry.optString("type")
            val itemPath = entry.optString("path")
            when (type) {
                "dir" -> listFiles(ref, itemPath, basePath, files, budget)
                "file" -> {
                    if (!isSafeRelativePath(itemPath, basePath)) error("invalid GitHub skill file path")
                    val relative = itemPath.removePrefix("$basePath/").ifBlank { itemPath }
                    require(relative != "SKILL.md" || itemPath.endsWith("/SKILL.md") || basePath.isBlank()) {
                        "invalid SKILL.md path"
                    }
                    budget.files++
                    require(budget.files <= MAX_FILES) { "GitHub skill contains too many files" }
                    val downloadUrl = entry.optString("download_url")
                    require(isRawGitHubUrl(downloadUrl)) { "invalid GitHub file URL" }
                    val content = decodeUtf8(requestBytes(downloadUrl, MAX_FILE_BYTES), itemPath)
                    budget.bytes += content.toByteArray(Charsets.UTF_8).size
                    require(budget.bytes <= MAX_TOTAL_BYTES) { "GitHub skill exceeds total size cap" }
                    files[relative] = content
                }
            }
        }
        budget.depth--
    }

    private fun request(url: String, maxBytes: Int = MAX_API_BYTES): String {
        return decodeUtf8(requestBytes(url, maxBytes), url)
    }

    private fun requestBytes(url: String, maxBytes: Int): ByteArray {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub API ${response.code}: ${response.message}")
            val body = response.body ?: error("GitHub response body was empty")
            return readBounded(body.byteStream(), maxBytes)
        }
    }

    private data class Budget(var files: Int = 0, var bytes: Int = 0, var depth: Int = 0, var nodes: Int = 0)

    companion object {
        const val MAX_FILES = 256
        const val MAX_TOTAL_BYTES = 10 * 1024 * 1024
        private const val MAX_FILE_BYTES = 1024 * 1024
        private const val MAX_API_BYTES = 2 * 1024 * 1024
        private const val MAX_DEPTH = 32
        private const val MAX_NODES = 512

        internal fun contentsUrl(ref: GitHubSkillSource, apiPath: String): String = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.github.com")
            .addPathSegments("repos/${ref.owner}/${ref.repo}/contents/${apiPath.trimStart('/')}")
            .addQueryParameter("ref", ref.branch)
            .apply {
                ref.queryParameters
                    .filter { (name, _) -> !name.equals("ref", ignoreCase = true) }
                    .forEach { (name, value) -> addQueryParameter(name, value) }
            }
            .build()
            .toString()

        internal fun isSafeRelativePath(path: String, basePath: String): Boolean {
            val relative = path.removePrefix("$basePath/")
            val isUnderBase = basePath.isBlank() || path == basePath || path.startsWith("$basePath/")
            return isUnderBase && relative.isNotBlank() && !relative.startsWith('/') &&
                !relative.contains('\\') && relative.split('/').none { it == ".." || it.isBlank() }
        }

        private fun isRawGitHubUrl(value: String): Boolean = runCatching {
            val url = value.toHttpUrl()
            url.scheme == "https" && url.host == "raw.githubusercontent.com"
        }.getOrDefault(false)

        internal fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "GitHub response exceeds size cap" }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }

        internal fun decodeUtf8(bytes: ByteArray, source: String): String {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (error: CharacterCodingException) {
                error("GitHub skill asset is not valid UTF-8: $source")
            }
            require(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                "GitHub skill asset is not a byte-preserving UTF-8 file: $source"
            }
            return text
        }

        private fun sha256(files: Map<String, String>): String = MessageDigest.getInstance("SHA-256")
            .digest(files.toSortedMap().entries.joinToString { "${it.key}\u0000${it.value}" }.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class GitHubSkillSource(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String,
    val queryParameters: List<Pair<String, String?>> = emptyList(),
) {
    companion object {
        fun parse(source: String): GitHubSkillSource? {
            val url = runCatching { source.trim().toHttpUrl() }.getOrNull() ?: return null
            if (url.scheme != "https" || url.host != "github.com") return null
            val segments = url.pathSegments
            if (segments.size < 2 || segments[0].isBlank() || segments[1].isBlank()) return null
            val queryParameters = url.queryParameterNames.flatMap { name ->
                url.queryParameterValues(name).map { value -> name to value }
            }
            val tree = segments.drop(2)
            if (tree.isNotEmpty() && tree.first() != "tree") return null
            val branch = tree.getOrNull(1).orEmpty().ifBlank { "HEAD" }
            val path = tree.drop(2).joinToString("/").trim('/').replace("//", "/")
            val explicitRef = url.queryParameter("ref")?.takeIf { it.isNotBlank() }
            return GitHubSkillSource(
                owner = segments[0],
                repo = segments[1],
                branch = explicitRef ?: branch,
                path = path,
                queryParameters = queryParameters,
            )
        }
    }
}

private fun String.githubSkillMarkdownUrl(): String {
    val parsed = GitHubSkillSource.parse(this) ?: return this
    val path = parsed.path.let { if (it.isBlank()) "SKILL.md" else "$it/SKILL.md" }
    val raw = HttpUrl.Builder()
        .scheme("https")
        .host("raw.githubusercontent.com")
        .addPathSegments("${parsed.owner}/${parsed.repo}/${parsed.branch}/$path")
    parsed.queryParameters.forEach { (name, value) -> raw.addQueryParameter(name, value) }
    return raw.build().toString()
}

class GitHubPluginAdapter(
    context: Context,
    private val httpClient: OkHttpClient,
) : ArtifactSourceAdapter {
    private val cacheDir = File(context.cacheDir, "artifact-imports")

    private companion object {
        const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
    }

    override fun supports(source: String): Boolean =
        runCatching { PluginManager.parsePluginSource(source) }.isSuccess

    override suspend fun prepare(source: String): Result<ImportCandidate> = try {
        withContext(Dispatchers.IO) {
            val parsed = PluginManager.parsePluginSource(source)
            val request = Request.Builder()
                .url(PluginManager.pluginArchiveUrl(parsed))
                .header("Accept", "application/vnd.github+json")
                .build()
            cacheDir.mkdirs()
            val file = File(cacheDir, "plugin-${System.nanoTime()}.zip")
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("GitHub API ${response.code}: ${response.message}")
                    val body = response.body ?: error("GitHub response body was empty")
                    require(body.contentLength() <= MAX_ARCHIVE_BYTES) { "plugin archive exceeds 50 MB" }
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyToBounded(output, MAX_ARCHIVE_BYTES) }
                    }
                }
                Result.success(ImportCandidate(
                    kind = ArtifactKind.PLUGIN,
                    name = parsed.repo,
                    description = "GitHub plugin ${parsed.owner}/${parsed.repo}",
                    provenance = ArtifactProvenance(source, ArtifactSourceKind.GITHUB, System.currentTimeMillis(), sha256(file)),
                    payload = ImportPayload.PluginArchive(file),
                ))
            } catch (throwable: Throwable) {
                file.delete()
                throw throwable
            }
        }
    } catch (throwable: Throwable) {
        throwable.rethrowCancellation()
        Result.failure(throwable)
    }
}

private fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

private fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun InputStream.copyToBounded(output: OutputStream, maxBytes: Long) {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return
        total += count
        require(total <= maxBytes) { "response exceeds size cap" }
        output.write(buffer, 0, count)
    }
}
