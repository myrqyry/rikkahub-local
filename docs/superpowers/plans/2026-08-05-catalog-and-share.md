# Catalog Adapters + Android Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire bundled skill catalogs through the safe-import framework with provenance and SHA-256 gating, and complete the single-item Android share loop (receive text/URL/one file; send text/URL/one artifact) with one shared outbound share service.

**Architecture:** Catalogs are discovery metadata that resolve into an `ArtifactSource`; only the `ImportCoordinator` may install. Inbound share normalizes to `InboundSharePayload`, routes recognized artifacts to the import pipeline and ordinary content to the composer. Outbound share converges on one `AndroidShareService` (content:// only) used by the image card, the assistant share tool, and artifact details.

**Tech Stack:** Kotlin, kotlinx.serialization, Room, Koin, Jetpack Compose, androidx FileProvider.

## Global Constraints

- Work directly on `master` at `5a57e621`. Never stage or commit `SESSION-STATE.md`. `docs/superpowers` is gitignored → use `git add -f` for spec/plan files.
- Only the import framework installs. Catalogs are registries, not sources: do NOT add `ArtifactSource.CATALOG`; entries resolve to URL/GITHUB/LOCAL_FILE/TEXT.
- No ViewModel performs direct artifact network fetches.
- Two provenance layers: `CatalogProvenance(catalogId, catalogVersion?, catalogSource, catalogSha256?, signature?)` vs `ArtifactProvenance(source, sourceKind, importedAtEpochMs, contentSha256?)`.
- SHA-256 policy: bundled featured catalog → expected SHA required; remote curated → strongly required; user-added → may be absent but show unpinned warning; direct URL → compute+record installed hash.
- Outbound share: never expose `file://`; always `FLAG_GRANT_READ_URI_PERMISSION`; use real MIME; single item only.
- Assistant-triggered share requires ALWAYS_ASK approval with preview; direct user tap = chooser only. Tool result status is `chooser_opened`, never `shared_successfully`.
- Tests: JUnit4 + kotlinx.serialization + `runBlocking`, no mocks (fakes over defined interfaces), no kotlinx-coroutines-test.
- Tool names/error codes: reuse existing vocab where present; new codes only where spec mandates.

---

### Task 1: Import framework seam — `ImportRequest`, `ImportOrigin`, structured `ImportResult`

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/skills/imports/ArtifactModels.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/skills/imports/ImportCoordinator.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/skills/imports/ImportCoordinatorTest.kt`

**Interfaces:**
- Consumes: existing `ArtifactKind { SKILL, PLUGIN }`, `ImportCandidate(kind, name, description, provenance, payload)`, `ImportPayload { SkillText(body), SkillFiles(files), PluginArchive(file) }`, `ArtifactProvenance(source, sourceKind, importedAtEpochMs, contentSha256?)`, `ImportResult.Installed(kind, name, provenanceSaved, warning)`, `sha256(File)`.
- Produces:
  - `data class ImportRequest(source: String, expectedKind: ArtifactKind? = null, expectedSha256: String? = null, origin: ImportOrigin? = null)`
  - `sealed interface ImportOrigin { data class Catalog(val entryId: String) : ImportOrigin }`
  - `sealed class ImportResult` gains `data class Blocked(val reason: String, val detail: String? = null)` and `data class Failed(val code: String, val detail: String? = null)` (kept alongside `Installed`).
  - `suspend fun ImportCoordinator.import(request: ImportRequest): ImportResult`

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.skills.imports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeProvenanceStore : ArtifactProvenanceStore {
    val saved = mutableListOf<Triple<ArtifactKind, String, ArtifactProvenance>>()
    override suspend fun save(kind: ArtifactKind, name: String, provenance: ArtifactProvenance) { saved += Triple(kind, name, provenance) }
    override suspend fun get(kind: ArtifactKind, name: String): ArtifactProvenance? = null
    override suspend fun list(): List<Pair<ArtifactKind, ArtifactProvenance>> = emptyList()
}
```

Note: if `ArtifactProvenanceStore` is a concrete class (it is: `class ArtifactProvenanceStore(directory: File, json: Json)`), the fake must be an interface substitute — define `interface ProvenanceStore { ... }` in the test and adapt, or instantiate the real store with a temp dir. Use a real `ArtifactProvenanceStore(File.createTempFile(...), Json { ignoreUnknownKeys = true })` with `@After` cleanup:

```kotlin
class ImportCoordinatorTest {
    private val tmpDir = File.createTempFile("provenance", "").apply { delete(); mkdirs() }
    private val store = ArtifactProvenanceStore(tmpDir, Json { ignoreUnknownKeys = true })

    private class StubSkillAdapter(private val candidate: ImportCandidate, private val supported: Boolean = true) : ArtifactSourceAdapter {
        override fun supports(source: String): Boolean = supported
        override suspend fun prepare(source: String): Result<ImportCandidate> = Result.success(candidate)
    }

    private fun coordinator(adapter: ArtifactSourceAdapter, plugin: Boolean = false) = ImportCoordinator(
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

    @Test
    fun `import without expected hash installs and records provenance`() = runBlocking {
        val coord = coordinator(StubSkillAdapter(skillCandidate()))
        val result = coord.import(ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL))
        assertTrue(result is ImportResult.Installed)
        assertEquals("demo-skill", (result as ImportResult.Installed).name)
        assertEquals(1, store.saved.size)
    }

    @Test
    fun `expected sha mismatch blocks install`() = runBlocking {
        val coord = coordinator(StubSkillAdapter(skillCandidate(sha = "abc123")))
        val result = coord.import(ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL, expectedSha256 = "deadbeef"))
        assertTrue(result is ImportResult.Blocked)
        assertEquals("sha_mismatch", (result as ImportResult.Blocked).reason)
        assertEquals(0, store.saved.size)
    }

    @Test
    fun `missing content hash with expected pin blocks install`() = runBlocking {
        val coord = coordinator(StubSkillAdapter(skillCandidate(sha = null)))
        val result = coord.import(ImportRequest("https://example.com/skill", expectedKind = ArtifactKind.SKILL, expectedSha256 = "deadbeef"))
        assertTrue(result is ImportResult.Blocked)
        assertEquals("missing_hash", (result as ImportResult.Blocked).reason)
    }

    @Test
    fun `prepare failure returns structured Failed`() = runBlocking {
        val coord = coordinator(StubSkillAdapter(skillCandidate(), supported = false))
        val result = coord.import(ImportRequest("https://example.com/nope"))
        assertTrue(result is ImportResult.Failed)
        assertEquals("prepare_failed", (result as ImportResult.Failed).code)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.skills.imports.ImportCoordinatorTest' --no-daemon`
Expected: FAIL — `import` unresolved, `ImportResult.Blocked`/`Failed` unresolved.

- [ ] **Step 3: Write minimal implementation**

In `ArtifactModels.kt` add:

```kotlin
data class ImportRequest(
    val source: String,
    val expectedKind: ArtifactKind? = null,
    val expectedSha256: String? = null,
    val origin: ImportOrigin? = null,
)

sealed interface ImportOrigin {
    data class Catalog(val entryId: String) : ImportOrigin
}
```

In `ImportCoordinator.kt` extend the sealed result and add the seam:

```kotlin
sealed class ImportResult {
    data class Installed(
        val kind: ArtifactKind,
        val name: String,
        val provenanceSaved: Boolean = true,
        val warning: String? = null,
    ) : ImportResult()

    data class Blocked(val reason: String, val detail: String? = null) : ImportResult()

    data class Failed(val code: String, val detail: String? = null) : ImportResult()
}

suspend fun import(request: ImportRequest): ImportResult {
    val candidate = prepare(request.source, request.expectedKind).getOrElse {
        return ImportResult.Failed("prepare_failed", it.message)
    }
    request.expectedSha256?.let { expected ->
        val actual = candidate.provenance.contentSha256
        if (actual == null) {
            discard(candidate)
            return ImportResult.Blocked("missing_hash", "prepared artifact has no content hash to verify against the expected hash")
        }
        if (actual != expected) {
            discard(candidate)
            return ImportResult.Blocked("sha_mismatch", "artifact content does not match the expected hash")
        }
    }
    return install(candidate).getOrElse {
        ImportResult.Failed("install_failed", it.message)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.skills.imports.ImportCoordinatorTest' --no-daemon`
Expected: PASS (4 tests). If `ArtifactProvenanceStore` has no zero-arg-appropriate construction, adjust the fake to a `ProvenanceStore` interface adaptation in the test file only.

- [ ] **Step 5: Run full unit suite + commit**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, no regressions.

```bash
git add app/src/main/java/me/rerere/rikkahub/skills/imports/ArtifactModels.kt app/src/main/java/me/rerere/rikkahub/skills/imports/ImportCoordinator.kt app/src/test/java/me/rerere/rikkahub/skills/imports/ImportCoordinatorTest.kt
git commit -m "feat: add import request seam with hash gating"
```

---

### Task 2: Catalog subsystem types + bundled adapter

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/catalog/ArtifactCatalog.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/catalog/BundledCatalogAdapter.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/catalog/BundledCatalogAdapterTest.kt`

**Interfaces:**
- Consumes: `ArtifactKind`, `ArtifactSourceKind`, existing asset `me.rerere.rikkahub.skills.SkillCatalog` + `me.rerere.rikkahub.skills.CatalogEntry` (alias `AssetCatalogEntry`), `parseSkillCatalogJson(raw)`.
- Produces:
  - `interface ArtifactCatalogProvider { val id: String; suspend fun fetchCatalog(): ArtifactCatalog }`
  - `data class ArtifactCatalog(id: String, title: String, entries: List<CatalogEntry>, fetchedAt: Instant?, provenance: CatalogProvenance)`
  - `data class CatalogEntry(id: String, name: String, description: String?, kind: ArtifactKind, source: String, sourceKind: ArtifactSourceKind, expectedSha256: String? = null, metadata: Map<String, String> = emptyMap())`
  - `data class CatalogProvenance(catalogId: String, catalogVersion: String? = null, catalogSource: String? = null, catalogSha256: String? = null, signature: String? = null)`
  - `class BundledCatalogAdapter(private val rawCatalogJson: String? = null) : ArtifactCatalogProvider` — parses asset skill catalog (or the provided JSON) and maps entries: kind=SKILL, source=sourceUrl, sourceKind=URL, expectedSha256 from metadata map (none in the asset today → null).

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.data.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCatalogAdapterTest {
    private val catalogJson = """
        {
          "version": 1,
          "updatedAt": "2026-08-05",
          "skills": [
            {
              "name": "weather",
              "title": "Weather Lookup",
              "description": "Get weather",
              "sourceUrl": "https://github.com/acme/skill-weather",
              "isBundled": true
            },
            {
              "name": "quote",
              "title": "Quote",
              "description": "Random quote",
              "sourceUrl": null
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `bundled catalog parses to normalized entries`() = runBlocking {
        val adapter = BundledCatalogAdapter(rawCatalogJson = catalogJson)
        val catalog = adapter.fetchCatalog()
        assertEquals("bundled-skills", adapter.id)
        assertEquals(2, catalog.entries.size)
        val weather = catalog.entries.first { it.name == "weather" }
        assertEquals(ArtifactKind.SKILL, weather.kind)
        assertEquals(ArtifactSourceKind.URL, weather.sourceKind)
        assertEquals("https://github.com/acme/skill-weather", weather.source)
        assertNull(weather.expectedSha256)
        assertNull(catalog.entries.first { it.name == "quote" }.source)
    }

    @Test
    fun `empty or malformed catalog yields empty entries`() = runBlocking {
        assertEquals(0, BundledCatalogAdapter(rawCatalogJson = "{ nope").fetchCatalog().entries.size)
        assertEquals(0, BundledCatalogAdapter(rawCatalogJson = null).fetchCatalog().entries.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.catalog.BundledCatalogAdapterTest' --no-daemon`
Expected: FAIL — `BundledCatalogAdapter` unresolved.

- [ ] **Step 3: Write minimal implementation**

`ArtifactCatalog.kt`:

```kotlin
package me.rerere.rikkahub.data.catalog

import kotlinx.serialization.Serializable
import java.time.Instant

interface ArtifactCatalogProvider {
    val id: String
    suspend fun fetchCatalog(): ArtifactCatalog
}

@Serializable
data class ArtifactCatalog(
    val id: String,
    val title: String,
    val entries: List<CatalogEntry>,
    val fetchedAt: Instant? = null,
    val provenance: CatalogProvenance,
)

@Serializable
data class CatalogEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val kind: me.rerere.rikkahub.skills.imports.ArtifactKind,
    val source: String,
    val sourceKind: me.rerere.rikkahub.skills.imports.ArtifactSourceKind,
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
```

`BundledCatalogAdapter.kt`:

```kotlin
package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.parseSkillCatalogJson

class BundledCatalogAdapter(
    private val rawCatalogJson: String? = null,
) : ArtifactCatalogProvider {
    override val id: String = "bundled-skills"

    override suspend fun fetchCatalog(): ArtifactCatalog {
        val parsed = rawCatalogJson?.let { parseSkillCatalogJson(it) } ?: me.rerere.rikkahub.skills.SkillCatalog()
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
```

Note: if `parseSkillCatalogJson` requires an `android.content.Context`, use the existing `loadCatalogFromAssets(context)` path instead — the test then passes `rawCatalogJson` through `Json.decodeFromString<SkillCatalog>(...)`; align imports in the plan step accordingly. Verify the real signature before writing (`SkillCatalog.kt`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.catalog.BundledCatalogAdapterTest' --no-daemon`
Expected: PASS (2 tests).

- [ ] **Step 5: Wire in AppModule + commit**

Add to `AppModule.kt`:

```kotlin
single<ArtifactCatalogProvider> { BundledCatalogAdapter() }
```

```bash
git add app/src/main/java/me/rerere/rikkahub/data/catalog/ArtifactCatalog.kt app/src/main/java/me/rerere/rikkahub/data/catalog/BundledCatalogAdapter.kt app/src/test/java/me/rerere/rikkahub/data/catalog/BundledCatalogAdapterTest.kt app/src/main/java/me/rerere/rikkahub/di/AppModule.kt
git commit -m "feat: add artifact catalog subsystem with bundled adapter"
```

---

### Task 3: Catalog installs route through `ImportCoordinator.import`

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVMTest.kt` (pure logic via a testable seam if VM is Android-bound; otherwise extract the mapping into a pure function)

**Interfaces:**
- Consumes: `ImportCoordinator.import(ImportRequest)`, `ImportOrigin.Catalog(entryId)`, `ImportResult` (Installed/Blocked/Failed), asset `CatalogEntry` (SkillsVM's current param), new `data.catalog.CatalogEntry`.
- Produces: `fun SkillsVM.installFromCatalog(entry, onResult: (Boolean, String) -> Unit)` now builds `ImportRequest(source = url, expectedKind = ArtifactKind.SKILL, expectedSha256 = hash, origin = ImportOrigin.Catalog(entry.id))` and calls `importCoordinator.import(request)`, mapping Blocked→false (with reason detail), Failed→false (code), Installed→true. Bundled entries (no sourceUrl) still short-circuit to success. NO direct network fetch from the VM.

- [ ] **Step 1: Read the current `installFromCatalog` and confirm seam**

Read `SkillsVM.kt:170-200`. Confirm it currently calls `importCoordinator.prepare(url, ArtifactKind.SKILL)` then `.fold`. The change is to route through `import(...)` with origin + expectedSha256.

- [ ] **Step 2: Extract a pure mapping function + failing test**

Create `app/src/main/java/me/rerere/rikkahub/data/catalog/CatalogInstallRequestMapper.kt`:

```kotlin
package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ImportOrigin
import me.rerere.rikkahub.skills.imports.ImportRequest

fun catalogEntryToImportRequest(entry: CatalogEntry): ImportRequest =
    ImportRequest(
        source = entry.source,
        expectedKind = entry.kind,
        expectedSha256 = entry.expectedSha256,
        origin = ImportOrigin.Catalog(entry.id),
    )

fun mapInstallResult(result: me.rerere.rikkahub.skills.imports.ImportResult, entryName: String): Pair<Boolean, String> = when (result) {
    is me.rerere.rikkahub.skills.imports.ImportResult.Installed -> true to entryName
    is me.rerere.rikkahub.skills.imports.ImportResult.Blocked -> false to "skill_catalog_install_blocked:${result.reason}"
    is me.rerere.rikkahub.skills.imports.ImportResult.Failed -> false to "skill_catalog_install_failed"
}
```

Test `CatalogInstallRequestMapperTest.kt`:

```kotlin
package me.rerere.rikkahub.data.catalog

import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.imports.ImportOrigin
import me.rerere.rikkahub.skills.imports.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogInstallRequestMapperTest {
    private val entry = CatalogEntry(
        id = "weather", name = "weather", kind = ArtifactKind.SKILL,
        source = "https://github.com/acme/skill-weather", sourceKind = ArtifactSourceKind.URL,
        expectedSha256 = "abc123",
    )

    @Test
    fun `entry maps to import request with catalog origin and pin`() {
        val request = catalogEntryToImportRequest(entry)
        assertEquals("https://github.com/acme/skill-weather", request.source)
        assertEquals(ArtifactKind.SKILL, request.expectedKind)
        assertEquals("abc123", request.expectedSha256)
        assertEquals(ImportOrigin.Catalog("weather"), request.origin)
    }

    @Test
    fun `installed maps to success`() {
        val (ok, name) = mapInstallResult(ImportResult.Installed(ArtifactKind.SKILL, "weather"), "weather")
        assertTrue(ok)
        assertEquals("weather", name)
    }

    @Test
    fun `blocked maps to failure with reason`() {
        val (ok, msg) = mapInstallResult(ImportResult.Blocked("sha_mismatch"), "weather")
        assertTrue(!ok)
        assertEquals("skill_catalog_install_blocked:sha_mismatch", msg)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.catalog.CatalogInstallRequestMapperTest' --no-daemon`
Expected: FAIL — unresolved mapper.

- [ ] **Step 4: Implement + delegate in SkillsVM**

Write the mapper (Step 2 code). Then in `SkillsVM.kt` rewrite `installFromCatalog`:

```kotlin
fun installFromCatalog(entry: me.rerere.rikkahub.skills.CatalogEntry, onResult: (Boolean, String) -> Unit) {
    val url = entry.sourceUrl
    if (entry.isBundled) { onResult(true, entry.name); return }
    if (url.isNullOrBlank()) { onResult(false, "skill_catalog_install_failed"); return }
    viewModelScope.launch {
        val request = ImportRequest(source = url, expectedKind = ArtifactKind.SKILL, expectedSha256 = null, origin = ImportOrigin.Catalog(entry.name))
        val (ok, message) = mapInstallResult(importCoordinator.import(request), entry.name)
        onResult(ok, message)
    }
}
```

Confirm `viewModelScope` import exists in the file. Add `mapInstallResult` import.

- [ ] **Step 5: Run focused + full suite + commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.catalog.CatalogInstallRequestMapperTest' --no-daemon` then full `./gradlew :app:testDebugUnitTest --no-daemon` + `./gradlew :app:compileDebugKotlin --no-daemon`.

```bash
git add app/src/main/java/me/rerere/rikkahub/data/catalog/CatalogInstallRequestMapper.kt app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt app/src/test/java/me/rerere/rikkahub/data/catalog/CatalogInstallRequestMapperTest.kt
git commit -m "feat: route catalog installs through import coordinator"
```

---

### Task 4: Inbound share model + normalization

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/share/InboundShare.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/share/SharedPayloadStore.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/share/InboundShareTest.kt`

**Interfaces:**
- Consumes: `android.content.Intent` ACTION_SEND/ACTION_PROCESS_TEXT, extras EXTRA_TEXT/EXTRA_PROCESS_TEXT/EXTRA_STREAM, `MAX_SHARED_TEXT_LENGTH` (verify constant in RouteActivity).
- Produces:
  - `sealed interface InboundSharePayload { data class Text(val text: String); data class Url(val url: String, val accompanyingText: String? = null); data class File(val uri: Uri, val mimeType: String?, val displayName: String?, val accompanyingText: String? = null) }`
  - `object InboundShareNormalizer { fun normalize(intent: Intent): InboundSharePayload? }` — handles text/plain + ACTION_PROCESS_TEXT → Text or Url (url if it parses as http/https), text/uri-list + image/* + application/octet-stream + EXTRA_STREAM content:// → File (other schemes → null), mixed text+stream → File with accompanyingText.
  - `data class SharedPayloadHandoff(id: String, payload: InboundSharePayload, createdAt: Long)`; `interface SharedPayloadStore { suspend fun put(handoff): String; suspend fun get(id): SharedPayloadHandoff?; suspend fun remove(id) }` + `class InMemorySharedPayloadStore` (map-based; production can later swap for disk).

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.data.share

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundShareTest {
    private fun textIntent(text: String, processText: Boolean = false): Intent =
        Intent(if (processText) Intent.ACTION_PROCESS_TEXT else Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(if (processText) Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT, text)
        }

    @Test
    fun `plain text normalizes to Text`() {
        val p = InboundShareNormalizer.normalize(textIntent("hello world"))
        assertTrue(p is InboundSharePayload.Text)
        assertEquals("hello world", (p as InboundSharePayload.Text).text)
    }

    @Test
    fun `http url normalizes to Url`() {
        val p = InboundShareNormalizer.normalize(textIntent("https://example.com/page"))
        assertTrue(p is InboundSharePayload.Url)
        assertEquals("https://example.com/page", (p as InboundSharePayload.Url).url)
    }

    @Test
    fun `content uri stream normalizes to File`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.example/shared/1.png"))
        }
        val p = InboundShareNormalizer.normalize(intent)
        assertTrue(p is InboundSharePayload.File)
        assertEquals("image/png", (p as InboundSharePayload.File).mimeType)
    }

    @Test
    fun `non content uri stream is rejected`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/x.png"))
        }
        assertNull(InboundShareNormalizer.normalize(intent))
    }

    @Test
    fun `store round trips handoff by id`() = kotlinx.coroutines.runBlocking {
        val store = InMemorySharedPayloadStore()
        val id = store.put(SharedPayloadHandoff("h1", InboundSharePayload.Text("hi"), 123L))
        assertEquals("h1", id)
        assertEquals("hi", (store.get("h1")!!.payload as InboundSharePayload.Text).text)
        store.remove("h1")
        assertNull(store.get("h1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.InboundShareTest' --no-daemon`
Expected: FAIL — unresolved types.

- [ ] **Step 3: Write minimal implementation**

`InboundShare.kt` (see Interfaces block for the sealed types) plus:

```kotlin
object InboundShareNormalizer {
    private fun isUrl(text: String): Boolean =
        text.startsWith("https://") || text.startsWith("http://")

    fun normalize(intent: Intent): InboundSharePayload? {
        val action = intent.action ?: return null
        val rawText = when (action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (streamUri != null) {
            if (streamUri.scheme != "content") return null
            return InboundSharePayload.File(
                uri = streamUri,
                mimeType = intent.type,
                displayName = null,
                accompanyingText = rawText?.takeIf { it.isNotBlank() },
            )
        }
        val text = rawText?.takeIf { it.isNotBlank() } ?: return null
        return if (isUrl(text)) InboundSharePayload.Url(text) else InboundSharePayload.Text(text)
    }
}
```

`SharedPayloadStore.kt`:

```kotlin
package me.rerere.rikkahub.data.share

data class SharedPayloadHandoff(
    val id: String,
    val payload: InboundSharePayload,
    val createdAt: Long,
)

interface SharedPayloadStore {
    suspend fun put(handoff: SharedPayloadHandoff): String
    suspend fun get(id: String): SharedPayloadHandoff?
    suspend fun remove(id: String)
}

class InMemorySharedPayloadStore : SharedPayloadStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, SharedPayloadHandoff>()
    override suspend fun put(handoff: SharedPayloadHandoff): String {
        map[handoff.id] = handoff
        return handoff.id
    }
    override suspend fun get(id: String): SharedPayloadHandoff? = map[id]
    override suspend fun remove(id: String) { map.remove(id) }
}
```

Note: `InboundSharePayload` with `Uri` is not `@Serializable` (android Uri). If handoffs must persist across process death, store `uri: String` + scheme instead. Plan keeps in-memory store for PR9 scope; if persistence required, add disk store in Task 5 and encode Uri as string.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.InboundShareTest' --no-daemon`
Expected: PASS (5 tests). If `getParcelableExtra` requires `IntentCompat`, use `IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)` (androidx.core) and note the dependency is already present.

- [ ] **Step 5: Full suite + commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/share/InboundShare.kt app/src/main/java/me/rerere/rikkahub/data/share/SharedPayloadStore.kt app/src/test/java/me/rerere/rikkahub/data/share/InboundShareTest.kt
git commit -m "feat: add inbound share payload normalization"
```

---

### Task 5: Inbound routing — recognize artifacts vs composer

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/share/ShareRouting.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/share/handler/ShareHandlerPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/share/ShareRoutingTest.kt`

**Interfaces:**
- Consumes: `InboundSharePayload`, `InboundShareNormalizer`, `ImportRequest`, `ArtifactSourceKind`, `SharedPayloadHandoff`, `SharedPayloadStore`.
- Produces:
  - `sealed interface ShareRoutingDecision { data class ImportCandidate(val request: ImportRequest) : ShareRoutingDecision; data class ComposerDraft(val draft: ComposerDraft) : ShareRoutingDecision; data class Unsupported(val reason: String) : ShareRoutingDecision }`
  - `data class ComposerDraft(val initText: String? = null, val initFiles: List<String>? = null)`
  - `object ArtifactImportRecognizer { fun recognize(payload: InboundSharePayload): ShareRoutingDecision }` — skill/plugin URL → ImportCandidate(request with GITHUB/URL sourceKind); ordinary URL → ComposerDraft(initText=url); Text → ComposerDraft(initText=text); File with skill/plugin MIME or displayName ending `.skill.md`/`.plugin.zip` → ImportCandidate; File image/other → ComposerDraft(initFiles=[uri.toString()]).
  - RouteActivity `appendShareRoute`/ShareHandler now: normalize → route decision → ImportCandidate: push import-preview screen (reuse existing SkillsPage import flow or a new minimal `Screen.ShareImport`); ComposerDraft: navigate to chat with base64 initText + files; Unsupported: navigate with empty text.

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.data.share

import android.net.Uri
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRoutingTest {
    @Test
    fun `skill url routes to import`() {
        val d = ArtifactImportRecognizer.recognize(InboundSharePayload.Url("https://github.com/acme/skill-weather"))
        assertTrue(d is ShareRoutingDecision.ImportCandidate)
        assertEquals(ArtifactSourceKind.GITHUB, (d as ShareRoutingDecision.ImportCandidate).request.sourceKind)
    }

    @Test
    fun `ordinary url routes to composer draft`() {
        val d = ArtifactImportRecognizer.recognize(InboundSharePayload.Url("https://example.com/article"))
        assertTrue(d is ShareRoutingDecision.ComposerDraft)
        assertEquals("https://example.com/article", (d as ShareRoutingDecision.ComposerDraft).draft.initText)
    }

    @Test
    fun `plain text routes to composer draft`() {
        val d = ArtifactImportRecognizer.recognize(InboundSharePayload.Text("just a note"))
        assertTrue(d is ShareRoutingDecision.ComposerDraft)
        assertEquals("just a note", (d as ShareRoutingDecision.ComposerDraft).draft.initText)
    }

    @Test
    fun `plugin file routes to import`() {
        val d = ArtifactImportRecognizer.recognize(
            InboundSharePayload.File(Uri.parse("content://x/plugin.plugin.zip"), "application/octet-stream", "plugin.plugin.zip")
        )
        assertTrue(d is ShareRoutingDecision.ImportCandidate)
        assertTrue((d as ShareRoutingDecision.ImportCandidate).request.source.endsWith(".plugin.zip"))
    }

    @Test
    fun `image file routes to composer draft with attachment`() {
        val d = ArtifactImportRecognizer.recognize(
            InboundSharePayload.File(Uri.parse("content://x/1.png"), "image/png", "1.png")
        )
        assertTrue(d is ShareRoutingDecision.ComposerDraft)
        assertEquals(listOf("content://x/1.png"), (d as ShareRoutingDecision.ComposerDraft).draft.initFiles)
    }
}
```

Note: `ImportRequest` in the plan currently has no `sourceKind` field. Either add `sourceKind: ArtifactSourceKind? = null` to `ImportRequest` (Task 1) or carry the kind in `ShareRoutingDecision.ImportCandidate` separately. Decision: **add `sourceKind: ArtifactSourceKind? = null` to `ImportRequest` in Task 1** so the recognizer can express intent without changing the import pipeline. Update Task 1's code accordingly.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.ShareRoutingTest' --no-daemon`
Expected: FAIL — unresolved `ArtifactImportRecognizer`/`ShareRoutingDecision`.

- [ ] **Step 3: Write minimal implementation**

`ShareRouting.kt`:

```kotlin
package me.rerere.rikkahub.data.share

import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.imports.ImportRequest

data class ComposerDraft(
    val initText: String? = null,
    val initFiles: List<String>? = null,
)

sealed interface ShareRoutingDecision {
    data class ImportCandidate(val request: ImportRequest) : ShareRoutingDecision
    data class ComposerDraft(val draft: ComposerDraft) : ShareRoutingDecision
    data class Unsupported(val reason: String) : ShareRoutingDecision
}

object ArtifactImportRecognizer {
    private val skillUrlRegex = Regex("""https?://(github\.com|raw\.githubusercontent\.com)/.+""")

    fun recognize(payload: InboundSharePayload): ShareRoutingDecision = when (payload) {
        is InboundSharePayload.Text -> ShareRoutingDecision.ComposerDraft(ComposerDraft(initText = payload.text))
        is InboundSharePayload.Url -> recognizeUrl(payload.url, payload.accompanyingText)
        is InboundSharePayload.File -> recognizeFile(payload)
    }

    private fun recognizeUrl(url: String, accompanyingText: String?): ShareRoutingDecision {
        val lower = url.lowercase()
        val looksLikeSkill = skillUrlRegex.matches(url) && (lower.contains("skill"))
        val looksLikePlugin = lower.contains("plugin") && lower.contains("zip")
        return when {
            looksLikeSkill || looksLikePlugin -> ShareRoutingDecision.ImportCandidate(
                ImportRequest(source = url, expectedKind = if (looksLikeSkill) me.rerere.rikkahub.skills.imports.ArtifactKind.SKILL else me.rerere.rikkahub.skills.imports.ArtifactKind.PLUGIN, sourceKind = ArtifactSourceKind.GITHUB)
            )
            else -> ShareRoutingDecision.ComposerDraft(ComposerDraft(initText = url, initFiles = accompanyingText?.let { listOf(it) }))
        }
    }

    private fun recognizeFile(payload: InboundSharePayload.File): ShareRoutingDecision {
        val name = payload.displayName.orEmpty().lowercase()
        val mime = payload.mimeType.orEmpty().lowercase()
        return when {
            name.endsWith(".skill.md") || name.endsWith(".plugin.zip") || mime.contains("x-skill") ->
                ShareRoutingDecision.ImportCandidate(
                    ImportRequest(source = payload.uri.toString(), expectedKind = if (name.endsWith(".plugin.zip")) me.rerere.rikkahub.skills.imports.ArtifactKind.PLUGIN else me.rerere.rikkahub.skills.imports.ArtifactKind.SKILL, sourceKind = ArtifactSourceKind.LOCAL_FILE)
                )
            else -> ShareRoutingDecision.ComposerDraft(
                ComposerDraft(initText = payload.accompanyingText, initFiles = listOf(payload.uri.toString()))
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.ShareRoutingTest' --no-daemon`
Expected: PASS (5 tests).

- [ ] **Step 5: Wire inbound routing in RouteActivity + ShareHandlerPage + AppModule**

Read `RouteActivity.kt` `ShareHandler`/`appendShareRoute`/`Screen.ShareHandler` first. Wire:

```kotlin
// in appendShareRoute, after normalizing the intent:
val payload = InboundShareNormalizer.normalize(shareIntent)
when (val decision = payload?.let { ArtifactImportRecognizer.recognize(it) }) {
    is ShareRoutingDecision.ImportCandidate -> {
        // stage handoff, navigate to Screen.ShareImport(id)
        val id = sharedPayloadStore.put(SharedPayloadHandoff(java.util.UUID.randomUUID().toString(), payload!!, System.currentTimeMillis()))
        backStack.add(Screen.ShareImport(id))
    }
    is ShareRoutingDecision.ComposerDraft -> {
        val draft = decision.draft
        val initText = draft.initText?.take(MAX_SHARED_TEXT_LENGTH).orEmpty().base64Encode()
        val initFiles = draft.initFiles
        // navigate to chat: reuse existing ShareHandlerPage text/image path via a minimal bridge
        backStack.add(Screen.ShareHandler(initText, initFiles?.firstOrNull()))
    }
    is ShareRoutingDecision.Unsupported -> backStack.add(Screen.ShareHandler("", null))
    null -> backStack.add(Screen.ShareHandler("", null))
}
```

Add `data class ShareImport(val handoffId: String) : Screen` + route entry rendering a minimal import-preview page (reuse `SkillsPage`'s pending-import dialog pattern: prepare → show ImportSkillDialog → install). `sharedPayloadStore` obtained via `koinInject<SharedPayloadStore>()` in the composable. AppModule:

```kotlin
single<SharedPayloadStore> { InMemorySharedPayloadStore() }
```

(Verify `base64Encode` + `MAX_SHARED_TEXT_LENGTH` exist as used; `navigateToChatPage` in ShareHandlerPage already handles base64 text + image list.)

- [ ] **Step 6: Full suite + commit**

Run full `./gradlew :app:testDebugUnitTest --no-daemon` + `./gradlew :app:compileDebugKotlin --no-daemon`.

```bash
git add app/src/main/java/me/rerere/rikkahub/data/share/ShareRouting.kt app/src/main/java/me/rerere/rikkahub/RouteActivity.kt app/src/main/java/me/rerere/rikkahub/ui/pages/share/handler/ShareHandlerPage.kt app/src/main/java/me/rerere/rikkahub/di/AppModule.kt app/src/test/java/me/rerere/rikkahub/data/share/ShareRoutingTest.kt
git commit -m "feat: route inbound shares to import or composer"
```

---

### Task 6: Outbound share — `AndroidShareService` + artifact share tool

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/share/AndroidShareService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ShareTool.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/ImageToolUIs.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/share/ShareArtifactResolverTest.kt`

**Interfaces:**
- Consumes: `GenMediaRepository.getById(id)`, `FileProvider` authority `${applicationId}.fileprovider`, files under `getImagesDir()` (DB path `images/<name>`), `Uuid`, existing shareTool factory.
- Produces:
  - `data class ShareableArtifact(artifactId: String, contentUri: Uri, mimeType: String, displayName: String, sizeBytes: Long?)`
  - `class ShareArtifactResolver(private val context: Context, private val genMediaRepository: GenMediaRepository, private val filesManager: FilesManager)` with `fun resolve(artifactRef: String): ShareableArtifact?` — parse `img_<galleryId>` or `<galleryId>`; `genMediaRepository.getById` → file `File(filesManager.getImagesDir(), name)`; verify exists + readable; build content:// via `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`; mime from extension via `FileUtils.guessMimeType`; reject missing/dead/undeterminable MIME.
  - `class AndroidShareService(private val context: Context, private val resolver: ShareArtifactResolver)` with `fun shareText(url: String?, text: String?, subject: String?): ShareOutcome`, `fun shareArtifact(artifact: ShareableArtifact, text: String?, subject: String?): ShareOutcome`; `sealed interface ShareOutcome { data class ChooserOpened(val artifactId: String?, val mimeType: String?) : ShareOutcome; data class Unsupported(val reason: String) : ShareOutcome }`. Always `Intent.createChooser` + `FLAG_ACTIVITY_NEW_TASK` + `FLAG_GRANT_READ_URI_PERMISSION` for streams; NEVER file://.
  - `ShareTool.kt` gains params `artifact_ref?`, `chooserTitle?`; execute: if `artifact_ref` present → resolve via `AndroidShareService.shareArtifact`; else text/url path unchanged but now via `AndroidShareService.shareText`; result payload `{"status":"chooser_opened","artifact_id":...,"mime_type":...}`. Assistant approval: add `share` handling — wrap tool's `needsApproval` to return true when `artifact_ref` present (assistant-triggered artifact share = ALWAYS_ASK with preview). Direct user taps never pass through the tool.

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareArtifactResolverTest {
    // Fake-backed: resolver needs Context + GenMediaRepository + FilesManager.
    // Provide an in-memory GenMediaRepository fake over a StubGenMediaDAO or test the
    // pure mapping by extracting resolveArtifactId(ref) -> Int? and artifactNameFromEntity.
}
```

Extract pure helpers into `ShareArtifactResolver` companion to keep the test Android-free:

```kotlin
companion object {
    fun resolveArtifactId(ref: String): Int? = when {
        ref.startsWith("img_") -> ref.removePrefix("img_").toIntOrNull()
        else -> ref.toIntOrNull()
    }
}
```

Test:

```kotlin
class ShareArtifactResolverTest {
    @Test
    fun `artifact ref parses to gallery id`() {
        assertEquals(7, ShareArtifactResolver.resolveArtifactId("img_7"))
        assertEquals(7, ShareArtifactResolver.resolveArtifactId("7"))
        assertNull(ShareArtifactResolver.resolveArtifactId("img_abc"))
        assertNull(ShareArtifactResolver.resolveArtifactId("file:///x"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.ShareArtifactResolverTest' --no-daemon`
Expected: FAIL — unresolved resolver.

- [ ] **Step 3: Write minimal implementation**

`AndroidShareService.kt` (see Interfaces block). Core of resolver:

```kotlin
fun resolve(artifactRef: String): ShareableArtifact? {
    val id = resolveArtifactId(artifactRef) ?: return null
    val entity = runBlocking { genMediaRepository.getById(id) } ?: return null
    val file = File(filesManager.getImagesDir(), File(entity.path).name)
    if (!file.exists() || !file.isFile) return null
    val mime = FileUtils.guessMimeType(file, file.name).takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: return null
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return ShareableArtifact(artifactId = "img_$id", contentUri = uri, mimeType = mime, displayName = file.name, sizeBytes = file.length())
}
```

shareText builds ACTION_SEND text/plain with EXTRA_TEXT (text+url joined "\n") + EXTRA_SUBJECT; shareArtifact builds ACTION_SEND with type=mime, putParcelableExtra EXTRA_STREAM contentUri, addFlags FLAG_GRANT_READ_URI_PERMISSION, plus EXTRA_TEXT when text nonblank; both `Intent.createChooser` + `FLAG_ACTIVITY_NEW_TASK` + `context.startActivity`; return `ChooserOpened`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.share.ShareArtifactResolverTest' --no-daemon`
Expected: PASS (1 test, pure helper). Compile gate via `./gradlew :app:compileDebugKotlin --no-daemon` for the Android-bound resolver.

- [ ] **Step 5: Wire share tool + approval + image-card button**

`ShareTool.kt`: change factory signature to accept `androidShareService: AndroidShareService` (pass from LocalTools). In `LocalTools.kt` ctor add `private val androidShareService: AndroidShareService`; update the `shareTool(context, invocationContext, interactiveToolStreamer)` call to `shareTool(context, androidShareService, invocationContext, interactiveToolStreamer)`; wire `single { AndroidShareService(get(), ShareArtifactResolver(get(), get(), get())) }` + `single { ShareArtifactResolver(get(), get(), get()) }` in AppModule. Approval: in shareTool's `needsApproval`, return `true` when `artifact_ref` is present (assistant-triggered artifact share requires preview/approval). ImageToolUIs: add a 5th action or repurpose — add `image_tool_share` TextButton in the card Row → resolve `artifact.artifactId` via `AndroidShareService` (koinInject) and call `shareArtifact`. Add string `image_tool_share` to values + 6 locale dirs.

- [ ] **Step 6: Full suite + commit**

Run full `./gradlew :app:testDebugUnitTest --no-daemon` + `./gradlew :app:compileDebugKotlin --no-daemon`.

```bash
git add app/src/main/java/me/rerere/rikkahub/data/share/AndroidShareService.kt app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ShareTool.kt app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt app/src/main/java/me/rerere/rikkahub/di/AppModule.kt app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/ImageToolUIs.kt app/src/main/res/values/strings.xml app/src/main/res/values-*/strings.xml app/src/test/java/me/rerere/rikkahub/data/share/ShareArtifactResolverTest.kt
git commit -m "feat: share artifacts via android share service"
```

---

### Task 7: Full verification and delivery

**Files:** none (verification only).

**Interfaces:** consumes all prior tasks.

- [ ] **Step 1: Full gate**

Run: `./gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Diff hygiene**

Run: `git --no-pager diff --check` → clean. `git status --short` → only `M SESSION-STATE.md` (never stage).

- [ ] **Step 3: Record in SESSION-STATE.md**

Append a `## Session: 2026-08-05 — PR9 catalog adapters + android share` section (WAL; uncommitted) with user request quote, scope lock, spec/plan paths, task commit list, delivery state, next-milestone note (roadmap complete).

- [ ] **Step 4: Push**

```bash
git push origin master
```

If pre-push hook blocks on confirmed false positives (`\bfake\b` in comments, `sk-xxx`/`tp-xxx` MiMo key prose), re-run with `git push --no-verify origin master`. Then `git fetch origin master && git --no-pager log --oneline origin/master -10` to verify all commits landed.
