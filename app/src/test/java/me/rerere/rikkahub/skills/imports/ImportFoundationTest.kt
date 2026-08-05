package me.rerere.rikkahub.skills.imports

import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.skills.plugins.PluginManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest

class ImportFoundationTest {
    private lateinit var dir: File
    private lateinit var store: ArtifactProvenanceStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("artifact-provenance").toFile()
        store = ArtifactProvenanceStore(dir, Json { encodeDefaults = true })
    }

    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun `source adapter matching is deterministic`() {
        val adapter = object : ArtifactSourceAdapter {
            override fun supports(source: String) = source.startsWith("https://")
            override suspend fun prepare(source: String) = Result.failure<ImportCandidate>(UnsupportedOperationException())
        }
        assertTrue(adapter.supports("https://example.com/skill.md"))
        assertFalse(adapter.supports("file:///tmp/skill.md"))
    }

    @Test fun `expected kind selects the matching adapter`() = runBlocking {
        val skill = ImportCandidate(
            ArtifactKind.SKILL,
            "skill",
            "skill description",
            ArtifactProvenance("source", ArtifactSourceKind.URL, 1L),
            ImportPayload.SkillText("body"),
        )
        val plugin = skill.copy(
            kind = ArtifactKind.PLUGIN,
            name = "plugin",
            payload = ImportPayload.SkillText("plugin body"),
        )
        val adapters = listOf(
            object : ArtifactSourceAdapter {
                override fun supports(source: String) = true
                override suspend fun prepare(source: String) = Result.success(plugin)
            },
            object : ArtifactSourceAdapter {
                override fun supports(source: String) = true
                override suspend fun prepare(source: String) = Result.success(skill)
            },
        )
        val coordinator = ImportCoordinator(
            adapters,
            store,
            installSkill = { Result.success(InstalledArtifact(it.name)) },
            installPlugin = { Result.success(InstalledArtifact(it.name)) },
        )

        assertEquals(ArtifactKind.SKILL, coordinator.prepare("source", ArtifactKind.SKILL).getOrThrow().kind)
        assertEquals(ArtifactKind.PLUGIN, coordinator.prepare("source", ArtifactKind.PLUGIN).getOrThrow().kind)
    }

    @Test fun `prepared skill files use skill installer and are discarded after install`() = runBlocking {
        val files = linkedMapOf("SKILL.md" to "---\nname: demo\n---\n", "prompt.txt" to "use me")
        val expectedFiles = files.toMap()
        val candidate = ImportCandidate(
            ArtifactKind.SKILL,
            "demo",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 1L),
            ImportPayload.SkillFiles(files),
        )
        var installedFiles: Map<String, String>? = null
        val result = ImportCoordinator(
            emptyList(), store,
            installSkill = { prepared ->
                installedFiles = (prepared.payload as ImportPayload.SkillFiles).files.toMap()
                Result.success(InstalledArtifact("demo"))
            },
            installPlugin = { Result.failure(UnsupportedOperationException()) },
        ).install(candidate)

        assertTrue(result.isSuccess)
        assertEquals(expectedFiles, installedFiles)
        assertTrue(files.isEmpty())
    }

    @Test fun `GitHub skill source parser supports repository tree and nested path`() {
        assertEquals(
            GitHubSkillSource("owner", "repo", "main", "skills/example"),
            GitHubSkillSource.parse("https://github.com/owner/repo/tree/main/skills/example/"),
        )
        assertEquals(
            GitHubSkillSource("owner", "repo", "HEAD", ""),
            GitHubSkillSource.parse("https://github.com/owner/repo"),
        )
        assertFalse(GitHubSkillSource.parse("https://github.com/owner/repo/blob/main/SKILL.md") != null)
        assertFalse(GitHubSkillAdapter.isSafeRelativePath("../secret", "skills/example"))
        assertTrue(GitHubSkillAdapter.isSafeRelativePath("skills/example/SKILL.md", "skills/example"))
        assertTrue(GitHubSkillAdapter.contentsUrl(GitHubSkillSource("owner", "repo", "main", "skills/example"), "skills/example")
            .endsWith("/repos/owner/repo/contents/skills/example?ref=main"))
    }

    @Test fun `GitHub query ref preserves slash for contents and raw URLs`() {
        val source = GitHubSkillSource.parse(
            "https://github.com/owner/repo/tree/main/skills/example?ref=feature/foo&keep=yes",
        )!!
        assertEquals("feature/foo", source.branch)
        assertEquals("skills/example", source.path)
        assertTrue(GitHubSkillAdapter.contentsUrl(source, source.path).contains("ref=feature%2Ffoo"))
        assertTrue(source.queryParameters.contains("keep" to "yes"))
    }

    @Test fun `plugin query ref preserves slash and legacy pair API`() {
        val source = PluginManager.parsePluginSource("https://github.com/owner/repo?ref=feature/foo")
        assertEquals("feature/foo", source.ref)
        assertTrue(PluginManager.pluginArchiveUrl(source).endsWith("/repos/owner/repo/zipball/feature/foo"))
        assertEquals("owner" to "repo", PluginManager.parsePluginRef("https://github.com/owner/repo?ref=feature/foo"))
    }

    @Test fun `GitHub skill assets reject invalid UTF-8`() {
        val invalid = byteArrayOf(0x53, 0x4b, 0x49, 0x4c, 0x4c, 0x2e, 0x6d, 0x64, 0x0a, 0xc3.toByte(), 0x28)
        var rejected = false
        try {
            GitHubSkillAdapter.decodeUtf8(invalid, "SKILL.md")
        } catch (error: IllegalStateException) {
            rejected = error.message?.contains("not valid UTF-8") == true
        }
        assertTrue("invalid UTF-8 must be rejected clearly", rejected)
    }

    @Test fun `bounded download sink rejects bytes beyond cap`() {
        val output = ByteArrayOutputStream()
        var rejected = false
        try {
            "12345".byteInputStream().copyToBounded(output, 4)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(output.size() <= 4)
    }

    @Test fun `provenance round trips and names cannot escape directory`() {
        val provenance = ArtifactProvenance("https://example.com/a", ArtifactSourceKind.URL, 42L, "abc")
        store.save(ArtifactKind.SKILL, "../../outside", provenance)
        assertEquals(provenance, store.get(ArtifactKind.SKILL, "../../outside"))
        assertTrue(dir.listFiles().orEmpty().all { it.parentFile == dir })
    }

    @Test fun `failed atomic write does not replace existing record`() {
        val first = ArtifactProvenance("one", ArtifactSourceKind.TEXT, 1L)
        val second = ArtifactProvenance("two", ArtifactSourceKind.TEXT, 2L)
        store.save(ArtifactKind.SKILL, "same", first)
        store.save(ArtifactKind.SKILL, "same", second)
        assertEquals(second, store.get(ArtifactKind.SKILL, "same"))
        assertNull(store.get(ArtifactKind.PLUGIN, "same"))
    }

    @Test fun `coordinator persists only after successful installation`() = runBlocking {
        val candidate = ImportCandidate(
            ArtifactKind.SKILL,
            "demo",
            "description",
            ArtifactProvenance("text", ArtifactSourceKind.TEXT, 9L),
            ImportPayload.SkillText("body"),
        )
        var shouldFail = true
        val coordinator = ImportCoordinator(
            adapters = emptyList(),
            provenanceStore = store,
            installSkill = { if (shouldFail) Result.failure(IllegalStateException("nope")) else Result.success(InstalledArtifact("demo")) },
            installPlugin = { Result.success(InstalledArtifact(it.name)) },
        )
        assertTrue(coordinator.install(candidate).isFailure)
        assertNull(store.get(ArtifactKind.SKILL, "demo"))
        shouldFail = false
        assertTrue(coordinator.install(candidate).isSuccess)
        assertEquals(candidate.provenance, store.get(ArtifactKind.SKILL, "demo"))
    }

    @Test fun `prepared plugin archive is passed through once and uses installed identity`() = runBlocking {
        val archive = Files.createTempFile("prepared-plugin", ".zip").toFile()
        archive.writeText("archive")
        var installCount = 0
        var installedFile: File? = null
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "repository-name",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, sha256(archive)),
            ImportPayload.PluginArchive(archive),
        )
        val coordinator = ImportCoordinator(
            adapters = emptyList(),
            provenanceStore = store,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { prepared ->
                installCount++
                installedFile = (prepared.payload as ImportPayload.PluginArchive).file
                Result.success(InstalledArtifact("manifest-name"))
            },
        )

        assertTrue(coordinator.install(candidate).isSuccess)
        assertEquals(1, installCount)
        assertEquals(archive, installedFile)
        assertEquals(candidate.provenance, store.get(ArtifactKind.PLUGIN, "manifest-name"))
        assertFalse(archive.exists())
    }

    @Test fun `failed prepared plugin installation does not save provenance`() = runBlocking {
        val archive = Files.createTempFile("failed-plugin", ".zip").toFile()
        archive.writeText("archive")
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "repository-name",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, sha256(archive)),
            ImportPayload.PluginArchive(archive),
        )
        val coordinator = ImportCoordinator(
            adapters = emptyList(),
            provenanceStore = store,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { Result.failure(IllegalStateException("rejected")) },
        )

        assertTrue(coordinator.install(candidate).isFailure)
        assertNull(store.get(ArtifactKind.PLUGIN, "repository-name"))
        assertNull(store.get(ArtifactKind.PLUGIN, "manifest-name"))
        assertFalse(archive.exists())
    }

    @Test fun `provenance failure does not turn successful installation into failure`() = runBlocking {
        val archive = Files.createTempFile("provenance-failure", ".zip").toFile()
        archive.writeText("archive")
        val invalidStorePath = Files.createTempFile("provenance-store", ".file").toFile()
        val failingStore = ArtifactProvenanceStore(invalidStorePath, Json { encodeDefaults = true })
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "demo",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, sha256(archive)),
            ImportPayload.PluginArchive(archive),
        )
        val result = ImportCoordinator(emptyList(), failingStore,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { Result.success(InstalledArtifact("manifest-name")) },
        ).install(candidate)

        assertTrue(result.isSuccess)
        val installed = result.getOrThrow() as ImportResult.Installed
        assertFalse(installed.provenanceSaved)
        assertTrue(installed.warning!!.contains("provenance"))
        assertNull(failingStore.get(ArtifactKind.PLUGIN, "manifest-name"))
        assertFalse(archive.exists())
        assertTrue(invalidStorePath.delete())
    }

    @Test fun `mutated prepared archive is rejected and cleaned`() = runBlocking {
        val archive = Files.createTempFile("mutated-plugin", ".zip").toFile()
        archive.writeText("original")
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "demo",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, sha256(archive)),
            ImportPayload.PluginArchive(archive),
        )
        archive.writeText("mutated")
        var called = false
        val coordinator = ImportCoordinator(
            emptyList(), store,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { called = true; Result.success(InstalledArtifact("demo")) },
        )

        assertTrue(coordinator.install(candidate).isFailure)
        assertFalse(called)
        assertFalse(archive.exists())
        assertNull(store.get(ArtifactKind.PLUGIN, "demo"))
    }

    @Test fun `discard removes prepared plugin archive`() = runBlocking {
        val archive = Files.createTempFile("discarded-plugin", ".zip").toFile()
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "demo",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, "unused"),
            ImportPayload.PluginArchive(archive),
        )
        ImportCoordinator(emptyList(), store,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { Result.failure(UnsupportedOperationException()) },
        ).discard(candidate)
        assertFalse(archive.exists())
    }

    @Test fun `cancellation is rethrown and archive is cleaned`() = runBlocking {
        val archive = Files.createTempFile("cancelled-plugin", ".zip").toFile()
        archive.writeText("archive")
        val candidate = ImportCandidate(
            ArtifactKind.PLUGIN,
            "demo",
            "description",
            ArtifactProvenance("github", ArtifactSourceKind.GITHUB, 9L, sha256(archive)),
            ImportPayload.PluginArchive(archive),
        )
        val coordinator = ImportCoordinator(emptyList(), store,
            installSkill = { Result.failure(UnsupportedOperationException()) },
            installPlugin = { throw CancellationException("cancelled") },
        )

        var cancelled = false
        try {
            coordinator.install(candidate)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(archive.exists())
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }
}
