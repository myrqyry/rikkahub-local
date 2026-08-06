package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.imports.ImportOrigin
import me.rerere.rikkahub.skills.imports.ImportRequest
import me.rerere.rikkahub.skills.imports.ImportResult

fun catalogEntryToImportRequest(entry: CatalogEntry): ImportRequest =
    ImportRequest(
        source = entry.source,
        expectedKind = entry.kind,
        expectedSha256 = entry.expectedSha256,
        origin = ImportOrigin.Catalog(entry.id),
    )

fun mapInstallResult(result: ImportResult, entryName: String): Pair<Boolean, String> = when (result) {
    is ImportResult.Installed -> true to entryName
    is ImportResult.Blocked -> false to "skill_catalog_install_blocked:${result.reason}"
    is ImportResult.Failed -> false to "skill_catalog_install_failed"
}
