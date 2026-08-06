package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.imports.ImportOrigin
import me.rerere.rikkahub.skills.imports.ImportRequest
import me.rerere.rikkahub.skills.imports.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogInstallRequestMapperTest {

    private val entry = CatalogEntry(
        id = "weather",
        name = "weather",
        kind = ArtifactKind.SKILL,
        source = "https://github.com/acme/skill-weather",
        sourceKind = ArtifactSourceKind.URL,
        expectedSha256 = "abc123",
    )

    @Test
    fun `catalog entry maps to import request with catalog origin and pinned hash`() {
        val request: ImportRequest = catalogEntryToImportRequest(entry)

        assertEquals("https://github.com/acme/skill-weather", request.source)
        assertEquals(ArtifactKind.SKILL, request.expectedKind)
        assertEquals("abc123", request.expectedSha256)
        assertTrue(request.origin is ImportOrigin.Catalog)
        assertEquals("weather", (request.origin as ImportOrigin.Catalog).entryId)
    }

    @Test
    fun `installed result maps to success with entry name`() {
        val (ok, message) = mapInstallResult(
            ImportResult.Installed(kind = ArtifactKind.SKILL, name = "weather"),
            "weather",
        )

        assertEquals(true, ok)
        assertEquals("weather", message)
    }

    @Test
    fun `blocked result maps to blocked message with reason`() {
        val (ok, message) = mapInstallResult(
            ImportResult.Blocked(reason = "sha_mismatch"),
            "weather",
        )

        assertEquals(false, ok)
        assertEquals("skill_catalog_install_blocked:sha_mismatch", message)
    }

    @Test
    fun `failed result maps to failed message`() {
        val (ok, message) = mapInstallResult(
            ImportResult.Failed(code = "install_failed"),
            "weather",
        )

        assertEquals(false, ok)
        assertEquals("skill_catalog_install_failed", message)
    }
}
