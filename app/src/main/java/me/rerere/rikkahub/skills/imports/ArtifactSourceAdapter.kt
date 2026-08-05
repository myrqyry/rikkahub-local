package me.rerere.rikkahub.skills.imports

interface ArtifactSourceAdapter {
    fun supports(source: String): Boolean
    suspend fun prepare(source: String): Result<ImportCandidate>
}
