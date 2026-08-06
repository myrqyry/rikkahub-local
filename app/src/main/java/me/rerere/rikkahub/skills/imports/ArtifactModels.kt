package me.rerere.rikkahub.skills.imports

import kotlinx.serialization.Serializable
import java.io.File

enum class ArtifactKind { SKILL, PLUGIN }

enum class ArtifactSourceKind { URL, LOCAL_FILE, GITHUB, TEXT }

sealed class ImportPayload {
    data class SkillText(val body: String) : ImportPayload()
    class SkillFiles(val files: MutableMap<String, String>) : ImportPayload()
    data class PluginArchive(val file: File) : ImportPayload()
}

@Serializable
data class ArtifactProvenance(
    val source: String,
    val sourceKind: ArtifactSourceKind,
    val importedAtEpochMs: Long,
    val contentSha256: String? = null,
)

data class ImportRequest(
    val source: String,
    val sourceKind: ArtifactSourceKind? = null,
    val expectedKind: ArtifactKind? = null,
    val expectedSha256: String? = null,
    val origin: ImportOrigin? = null,
)

sealed interface ImportOrigin {
    data class Catalog(val entryId: String) : ImportOrigin
}

data class ImportCandidate(
    val kind: ArtifactKind,
    val name: String,
    val description: String,
    val provenance: ArtifactProvenance,
    val payload: ImportPayload,
)
