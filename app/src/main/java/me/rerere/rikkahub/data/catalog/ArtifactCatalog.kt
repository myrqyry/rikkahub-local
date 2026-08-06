package me.rerere.rikkahub.data.catalog

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind

interface ArtifactCatalogProvider {
    val id: String
    suspend fun fetchCatalog(): ArtifactCatalog
}

@Serializable
data class ArtifactCatalog(
    val id: String,
    val title: String,
    val entries: List<CatalogEntry>,
    val fetchedAtEpochMs: Long? = null,
    val provenance: CatalogProvenance,
)

@Serializable
data class CatalogEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val kind: ArtifactKind,
    val source: String,
    val sourceKind: ArtifactSourceKind,
    val expectedSha256: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogProvenance(
    val catalogId: String,
    val catalogVersion: String? = null,
    val catalogSource: String? = null,
    val catalogSha256: String? = null,
    val signature: String? = null,
)
