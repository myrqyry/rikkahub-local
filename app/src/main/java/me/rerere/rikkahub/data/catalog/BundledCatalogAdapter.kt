package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.SkillCatalog
import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.parseSkillCatalogJson

class BundledCatalogAdapter(
    private val rawCatalogJson: String? = null,
) : ArtifactCatalogProvider {
    override val id: String = "bundled-skills"

    override suspend fun fetchCatalog(): ArtifactCatalog {
        val parsed = rawCatalogJson?.let { parseSkillCatalogJson(it) } ?: SkillCatalog()
        val entries = parsed.skills.mapNotNull { skill ->
            if (skill.sourceUrl.isNullOrBlank()) return@mapNotNull null
            CatalogEntry(
                id = skill.name,
                name = skill.name,
                description = skill.description,
                kind = ArtifactKind.SKILL,
                source = skill.sourceUrl,
                sourceKind = ArtifactSourceKind.URL,
                metadata = mapOf("bundled" to skill.isBundled.toString()),
            )
        }
        return ArtifactCatalog(
            id = id,
            title = "Featured Skills",
            entries = entries,
            provenance = CatalogProvenance(catalogId = id, catalogVersion = parsed.version.toString()),
        )
    }
}
