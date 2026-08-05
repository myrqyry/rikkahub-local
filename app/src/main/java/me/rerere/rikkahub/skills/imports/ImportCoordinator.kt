package me.rerere.rikkahub.skills.imports

import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest

class ImportCoordinator(
    private val adapters: List<ArtifactSourceAdapter>,
    private val provenanceStore: ArtifactProvenanceStore,
    private val installSkill: suspend (ImportCandidate) -> Result<InstalledArtifact>,
    private val installPlugin: suspend (ImportCandidate) -> Result<InstalledArtifact>,
) {
    suspend fun prepare(source: String, expectedKind: ArtifactKind? = null): Result<ImportCandidate> {
        val matching = adapters
            .filter { it.supports(source) }
            .sortedBy { adapter ->
                when {
                    expectedKind == ArtifactKind.SKILL &&
                        (adapter is GitHubSkillAdapter || adapter is SkillUrlAdapter) -> 0
                    expectedKind == ArtifactKind.PLUGIN && adapter is GitHubPluginAdapter -> 0
                    else -> 1
                }
            }
        if (matching.isEmpty()) {
            return Result.failure(IllegalArgumentException("unsupported artifact source"))
        }
        var lastFailure: Throwable? = null
        for (adapter in matching) {
            val result = adapter.prepare(source)
            if (result.isSuccess) {
                val candidate = result.getOrThrow()
                if (expectedKind == null || candidate.kind == expectedKind) return Result.success(candidate)
                cleanup(candidate)
                lastFailure = IllegalArgumentException("source did not produce a ${expectedKind.name.lowercase()}")
            } else {
                lastFailure = result.exceptionOrNull()
            }
        }
        return Result.failure(lastFailure ?: IllegalArgumentException("unsupported artifact source"))
    }

    suspend fun install(candidate: ImportCandidate): Result<ImportResult> {
        return try {
            verifyPreparedArchive(candidate)
            val installation = try {
                when (candidate.kind) {
                ArtifactKind.SKILL -> installSkill(candidate)
                ArtifactKind.PLUGIN -> installPlugin(candidate)
                }
            } catch (throwable: Throwable) {
                throwable.rethrowCancellation()
                Result.failure(throwable)
            }
            val installed = installation.getOrThrow()
            val provenance = try {
                provenanceStore.save(candidate.kind, installed.name, candidate.provenance)
                true to null
            } catch (provenanceError: Throwable) {
                provenanceError.rethrowCancellation()
                false to "installation succeeded, but provenance could not be saved: ${provenanceError.message ?: "unknown error"}"
            }
            Result.success(ImportResult.Installed(
                kind = candidate.kind,
                name = installed.name,
                provenanceSaved = provenance.first,
                warning = provenance.second,
            ))
        } catch (throwable: Throwable) {
            throwable.rethrowCancellation()
            Result.failure(throwable)
        } finally {
            cleanup(candidate)
        }
    }

    /** Discard a prepared candidate that will not be installed. */
    fun discard(candidate: ImportCandidate) {
        cleanup(candidate)
    }

    private fun verifyPreparedArchive(candidate: ImportCandidate) {
        if (candidate.kind != ArtifactKind.PLUGIN) return
        val archive = (candidate.payload as? ImportPayload.PluginArchive)?.file
            ?: error("plugin candidate has no archive payload")
        val expected = candidate.provenance.contentSha256
            ?: error("plugin candidate has no content hash")
        require(sha256(archive) == expected) { "prepared plugin archive was mutated" }
    }

    private fun cleanup(candidate: ImportCandidate) {
        (candidate.payload as? ImportPayload.SkillFiles)?.files?.clear()
        (candidate.payload as? ImportPayload.PluginArchive)?.file?.delete()
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun Throwable.rethrowCancellation() {
        if (this is CancellationException) throw this
    }
}

data class InstalledArtifact(val name: String)

sealed class ImportResult {
    data class Installed(
        val kind: ArtifactKind,
        val name: String,
        val provenanceSaved: Boolean = true,
        val warning: String? = null,
    ) : ImportResult()
}
