package me.rerere.rikkahub.skills.imports

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class ImportCoordinatorTest {
    private lateinit var dir: java.io.File
    private lateinit var store: ArtifactProvenanceStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("import-coordinator").toFile()
        store = ArtifactProvenanceStore(dir, Json { encodeDefaults = true })
    }

    @After fun tearDown() { dir.deleteRecursively() }

    private fun coordinator(adapter: ArtifactSourceAdapter) = ImportCoordinator(
        adapters = listOf(adapter),
        provenanceStore = store,
        installSkill = { Result.success(InstalledArtifact(it.name)) },
        installPlugin = { Result.success(InstalledArtifact(it.name)) },
    )

    private fun skillCandidate(sha: String? = null) = ImportCandidate(
        kind = ArtifactKind.SKILL,
        name = "demo-skill",
        description = "demo",
        provenance = ArtifactProvenance("https://example.com/skill", ArtifactSourceKind.URL, 1L, sha),
        payload = ImportPayload.SkillText("skill body"),
    )

    private fun skillAdapter(candidate: ImportCandidate, supported: Boolean = true) = object : ArtifactSourceAdapter {
        override fun supports(source: String): Boolean = supported
        override suspend fun prepare(source: String): Result<ImportCandidate> = Result.success(candidate)
    }

    @Test
    fun `import without expected hash installs and records provenance`() = runBlocking {
        val coord = coordinator(skillAdapter(skillCandidate()))
        val result = coord.import(ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL))
        assertTrue(result is ImportResult.Installed)
        assertEquals("demo-skill", (result as ImportResult.Installed).name)
        assertEquals(1, store.list().size)
    }

    @Test
    fun `expected sha mismatch blocks install`() = runBlocking {
        val coord = coordinator(skillAdapter(skillCandidate(sha = "abc123")))
        val result = coord.import(
            ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL, expectedSha256 = "deadbeef")
        )
        assertTrue(result is ImportResult.Blocked)
        assertEquals("sha_mismatch", (result as ImportResult.Blocked).reason)
        assertEquals(0, store.list().size)
    }

    @Test
    fun `missing content hash with expected pin blocks install`() = runBlocking {
        val coord = coordinator(skillAdapter(skillCandidate(sha = null)))
        val result = coord.import(
            ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL, expectedSha256 = "deadbeef")
        )
        assertTrue(result is ImportResult.Blocked)
        assertEquals("missing_hash", (result as ImportResult.Blocked).reason)
    }

    @Test
    fun `prepare failure returns structured Failed`() = runBlocking {
        val failing = object : ArtifactSourceAdapter {
            override fun supports(source: String): Boolean = true
            override suspend fun prepare(source: String): Result<ImportCandidate> =
                Result.failure(IllegalArgumentException("unsupported artifact source"))
        }
        val coord = coordinator(failing)
        val result = coord.import(ImportRequest("https://example.com/nope"))
        assertTrue(result is ImportResult.Failed)
        assertEquals("prepare_failed", (result as ImportResult.Failed).code)
    }
}
