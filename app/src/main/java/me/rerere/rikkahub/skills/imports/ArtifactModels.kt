package me.rerere.rikkahub.skills.imports

import kotlinx.serialization.Serializable
import java.io.File

enum class ArtifactKind { SKILL, PLUGIN }

enum class ArtifactSourceKind { URL, LOCAL_FILE, GITHUB, TEXT }

sealed class ImportPayload {
    data class SkillText(val body: String) : ImportPayload()
    data class PluginArchive(val file: File) : ImportPayload()
}

@Serializable
data class ArtifactProvenance(
    val source: String,
    val sourceKind: ArtifactSourceKind,
    val importedAtEpochMs: Long,
    val contentSha256: String? = null,
)

data class ImportCandidate(
    val kind: ArtifactKind,
    val name: String,
    val description: String,
    val provenance: ArtifactProvenance,
    val payload: ImportPayload,
)
