package me.rerere.rikkahub.skills.imports

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.skills.SkillUrlImporter
import me.rerere.rikkahub.skills.plugins.PluginManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class SkillUrlAdapter(private val importer: SkillUrlImporter) : ArtifactSourceAdapter {
    override fun supports(source: String): Boolean =
        importer.checkUrl(source) == null

    override suspend fun prepare(source: String): Result<ImportCandidate> = try {
        val prepared = importer.prepareFromUrl(source).getOrThrow()
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

class GitHubPluginAdapter(
    context: Context,
    private val httpClient: OkHttpClient,
) : ArtifactSourceAdapter {
    private val cacheDir = File(context.cacheDir, "artifact-imports")

    override fun supports(source: String): Boolean =
        runCatching { PluginManager.parsePluginRef(source) }.isSuccess

    override suspend fun prepare(source: String): Result<ImportCandidate> = try {
        withContext(Dispatchers.IO) {
            val (owner, repo) = PluginManager.parsePluginRef(source)
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/zipball")
                .header("Accept", "application/vnd.github+json")
                .build()
            val bytes = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub API ${response.code}: ${response.message}")
                response.body.bytes()
            }
            require(bytes.size <= 50 * 1024 * 1024) { "plugin archive exceeds 50 MB" }
            cacheDir.mkdirs()
            val file = File(cacheDir, "plugin-${System.nanoTime()}.zip")
            file.writeBytes(bytes)
            Result.success(ImportCandidate(
                kind = ArtifactKind.PLUGIN,
                name = repo,
                description = "GitHub plugin $owner/$repo",
                provenance = ArtifactProvenance(source, ArtifactSourceKind.GITHUB, System.currentTimeMillis(), sha256(bytes)),
                payload = ImportPayload.PluginArchive(file),
            ))
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
