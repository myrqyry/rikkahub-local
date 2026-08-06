# Catalog Adapters + Android Share — Design

## Goal

PR9 (final roadmap PR) delivers two subsystems:

1. **Catalog adapters**: bundled and remote artifact catalogs become *discovery metadata* that resolve into an `ArtifactSource`; only the existing safe-import framework (`ImportCoordinator` + `ArtifactSourceAdapter`) may install artifacts.
2. **Android share**: a complete single-item share loop — receive text/URL/one file from other apps (routing recognized skill/plugin artifacts to the import preview, ordinary content to the composer) and send text/URL/one artifact via the Sharesheet (shared by the PR8 image card, the assistant `share` tool, and artifact details).

## Architecture

```
ArtifactCatalogProvider → CatalogEntry → ArtifactSource → ImportCoordinator → ArtifactSourceAdapter

Inbound Android:  Intent → normalizer → recognizer → {ImportCoordinator | ComposerDraft}
Outbound Android: {image card | share tool | artifact details} → AndroidShareService → Sharesheet
```

## Global Constraints

- Only the import framework may install artifacts. No second import system.
- Catalogs are registries, not sources: entries resolve to `URL` / `GITHUB` / `LOCAL_FILE` / `TEXT` — do not add an `ArtifactSource.CATALOG` kind.
- No ViewModel performs direct network fetches of artifact content.
- Provenance: `CatalogProvenance(catalogId, catalogVersion?, catalogSource, catalogSha256?, signature?)` and `ArtifactProvenance(source, resolvedUrl?, resolvedCommit?, contentSha256, importedAt)` stay separate layers.
- SHA-256 policy: bundled featured catalog → expected SHA required; remote curated → strongly required; user-added → may be absent with unpinned warning; direct URL → compute + record installed hash.
- Outbound: never expose `file://` URIs to other apps; always grant read-URI permission; real MIME from artifact metadata.
- Assistant-triggered share requires approval with preview; direct user-triggered share uses the chooser as the action (no redundant approval).
- Tool result reports `{"status":"chooser_opened", ...}`, never "shared_successfully".
- Single-item sharing only. Defer ACTION_SEND_MULTIPLE, multiple artifacts, zip bundles, whole-conversation sharing, per-target integrations.
- Work on `master` at `5a57e621`. Never stage/commit `SESSION-STATE.md`. `docs/superpowers` is gitignored → `git add -f`.
- Tests: JUnit4 + kotlinx.serialization + `runBlocking` only; no `kotlinx-coroutines-test`; no mocks (fakes over defined interfaces).

## Part 1 — Catalog subsystem

### 1.1 Catalog types

```kotlin
interface ArtifactCatalogProvider {
    val id: String
    suspend fun fetchCatalog(): ArtifactCatalog
}

data class ArtifactCatalog(
    val id: String,
    val title: String,
    val entries: List<CatalogEntry>,
    val fetchedAt: Instant? = null,
    val provenance: CatalogProvenance,
)

data class CatalogEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val kind: ArtifactKind,          // SKILL | PLUGIN (existing enum)
    val source: ArtifactSource,      // URL | GITHUB | LOCAL_FILE | TEXT (existing enum)
    val expectedSha256: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class CatalogProvenance(
    val catalogId: String,
    val catalogVersion: String? = null,
    val catalogSource: String,       // e.g. "bundled:assets/skill-catalog.json"
    val catalogSha256: String? = null,
    val signature: String? = null,
)
```

Naming: new interfaces use "Provider" (never "Adapter" — that name belongs to the import framework's `ArtifactSourceAdapter`).

### 1.2 Import framework seam

Current API is `prepare(source, expectedKind?)` / `install(candidate)` / `discard(candidate)` — it cannot express an expected SHA or an origin. Add:

```kotlin
data class ImportRequest(
    val source: String,                       // raw source string
    val expectedKind: ArtifactKind? = null,
    val expectedSha256: String? = null,
    val origin: ImportOrigin? = null,
)

sealed interface ImportOrigin {
    data class Catalog(val entryId: String) : ImportOrigin
}

// ImportCoordinator gains:
suspend fun import(request: ImportRequest): ImportResult
```

`import()` runs the existing prepare → verifyPreparedArchive → install → provenance-save pipeline. `verifyPreparedArchive` already requires `contentSha256` for plugins; extend it so an `expectedSha256` mismatch fails before install with a structured `ImportResult.Failed` (or `ImportResult.Blocked`), and an absent pin on a bundled catalog is a warning, not a block.

### 1.3 BundledCatalogAdapter + SkillsVM cleanup

- `BundledCatalogAdapter : ArtifactCatalogProvider` wraps the existing asset `SkillCatalog`/`loadCatalogFromAssets`, mapping `CatalogEntry` (skills/SkillCatalog.kt) to the new `CatalogEntry` carrying `kind`, `source` (derived from `sourceUrl`), and `expectedSha256` from catalog metadata.
- **Cleanup (most important):** `SkillsVM.installFromCatalog()` currently fetches `entry.sourceUrl` directly. It MUST delegate to `ImportCoordinator.import(ImportRequest(...))`. No direct network fetch from the ViewModel.
- Install preview shows the same detail as manual import: Name / Kind / Source / Catalog / Expected hash / Resolved hash / Files / Warnings.

## Part 2 — Inbound share

### 2.1 Payload model + normalizer

```kotlin
sealed interface InboundSharePayload {
    data class Text(val text: String) : InboundSharePayload
    data class Url(val url: String, val accompanyingText: String? = null) : InboundSharePayload
    data class File(val uri: Uri, val mimeType: String? = null,
                    val displayName: String? = null, val accompanyingText: String? = null) : InboundSharePayload
}
```

`InboundShareNormalizer` reads the sanitized intent (reuse `RouteActivity.sanitizeIncomingShareIntent` semantics: content:// stream only) and produces one payload. Handle ACTION_SEND `text/plain` / `text/uri-list` / `image/*` / `application/octet-stream` and recognized skill/plugin MIME or filename.

### 2.2 Recognition + routing

```kotlin
sealed interface ShareRoutingDecision {
    data class ImportCandidate(val request: ImportRequest) : ShareRoutingDecision
    data class ComposerDraft(val draft: ComposerDraft) : ShareRoutingDecision
    data class Unsupported(val reason: String) : ShareRoutingDecision
}
```

`ArtifactImportRecognizer` routing rules:

| Inbound | Decision |
|---|---|
| Skill/plugin URL (shared GitHub URL, raw URL, recognized skill/plugin) | ImportCandidate |
| Skill/plugin file (MIME or filename match) | ImportCandidate |
| Ordinary URL | ComposerDraft (draft with URL text) |
| Plain text | ComposerDraft (draft with text) |
| Image / other ordinary file | ComposerDraft (draft with attachment) |

Do NOT force every shared file through the importer.

### 2.3 Durable handoff

```kotlin
data class SharedPayloadHandoff(
    val id: String,                  // navigation key only
    val payload: InboundSharePayload,
    val createdAt: Instant,
)
```

Navigate by `id` only (never stuff URIs/text into route strings). Stage transient `content://` into app-private storage before it is needed downstream (read metadata immediately; attempt persistable permission when supported; never assume a URI stays readable). A `SharedPayloadStore` persists handoffs so the share route can resolve by id.

## Part 3 — Outbound share

### 3.1 One AndroidShareService

Converge all outbound share on a single service — do not implement one path in Compose and another in tool execution.

```kotlin
class AndroidShareService(private val context: Context, private val artifactResolver: ShareArtifactResolver)

data class ShareableArtifact(
    val artifactId: String,
    val contentUri: Uri,             // content:// only
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long? = null,
)
```

`ShareArtifactResolver.resolve(artifactRef)` rejects: missing artifacts, dead temp files, non-shareable private URIs, unsupported schemes, direct `file://` exposure, undeterminable MIME. The `artifact_ref` resolves through the same resolver the PR8 tools use (artifact ID → gallery path → content:// via FileProvider).

### 3.2 Share tool extension

- `shareTool` params become `{text?, url?, artifact_ref?, chooserTitle?}`.
- text-only / url-only / text+url → ACTION_SEND `text/plain`.
- artifact_ref → resolve → content:// URI with real MIME + `FLAG_GRANT_READ_URI_PERMISSION` + `ACTION_SEND`.
- artifact_ref + text → stream + EXTRA_TEXT.
- Returns `{"status":"chooser_opened","artifact_id":...,"mime_type":"image/png"}`.

### 3.3 Approval

- Direct user tap → chooser is the action, no extra approval.
- Assistant invokes share tool → ALWAYS_ASK with preview (Image: name/dims/size; Text: quote) with [Continue][Cancel].

## Part 4 — Tests

Catalog (7):
1. Bundled catalog parses to normalized entries.
2. Entry maps to the correct ArtifactSource.
3. Skill catalog install uses ImportCoordinator.
4. Plugin catalog install uses ImportCoordinator.
5. Expected SHA mismatch blocks install.
6. Missing pin → intended unpinned/trust status.
7. No ViewModel performs direct artifact fetching.

Share (15):
1. Plain text → populated composer draft.
2. Ordinary URL → composer draft.
3. Skill URL → import preview.
4. GitHub plugin URL → correct adapter selected.
5. Recognized local file → ImportCoordinator.
6. Ordinary image → composer draft with attachment.
7. Transient content:// URI staged to app-private storage.
8. artifact_ref → content:// URI (never file://).
9. PNG artifact shares as image/png.
10. Read permission granted to chooser targets.
11. Missing artifact → structured failure.
12. Assistant share requires approval.
13. Direct user share has no redundant approval.
14. Tool result is chooser_opened, not delivered.
15. No file:// URI leaves the app.

## Deferred

ACTION_SEND_MULTIPLE, multiple artifacts, zip creation, whole-conversation sharing, workflow bundles, contact/location/calendar payloads, directory receiving, background delivery without chooser, per-target integrations, social reposting, export management, cross-module PartMetadata framework.
