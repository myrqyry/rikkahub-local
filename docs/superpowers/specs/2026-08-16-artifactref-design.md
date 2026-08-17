# Phase H — Canonical ArtifactRef (minimal slice) Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Status:** Approved by user (m1826)
**Date:** 2026-08-16

## 1. Background

The roadmap (`docs/references/architecture.md`) defines a canonical artifact
reference (`ArtifactRef`) that the Generation branch emits and that downstream
receipts adopt. No canonical type exists today — `ProcessReceipt` (Phase F)
carries a KDoc note: "An `outputRef` is added when the canonical ArtifactRef
lands (roadmap H)."

Existing app-side artifact-adjacent types (`MediaArtifactRef`,
`StoredImageArtifact`, `GenerationReceipt`) are app-module specific and cannot
be referenced from the pure local-llm seam.

## 2. Scope

Minimal slice only:

1. Canonical `ArtifactRef` data type in local-llm (pure JVM, `@Serializable`).
2. `ArtifactResolver` + `ArtifactSink` interfaces in local-llm (pure seams;
   app adapts via Koin DI).
3. `ProcessReceipt` gains an optional `outputRef: ArtifactRef?` field,
   fulfilling its KDoc deferral note.

Explicitly deferred (follow-up, not this phase): app-side adapter
implementations, GenerationService orchestration, Compute abstraction, Browser
sessions.

## 3. Decisions (user-approved)

- **Home module:** local-llm (pure seam so `ProcessReceipt` can adopt
  `outputRef` without an app dependency; follows the module-boundary rule).
- **Scope width:** Minimal ref + `ProcessReceipt` hook (not GenerationService).
- **Seam shape:** Separate `ArtifactResolver` (read) + `ArtifactSink` (write)
  interfaces, mirroring the existing `CapabilityGrantSource` →
  `ToolApprovalCapabilityGrantSource` boundary pattern.

## 4. ArtifactRef

Location: `local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactRef.kt`

```kotlin
@Serializable
enum class ArtifactKind {
    PROCESS_OUTPUT,
    IMAGE,
    DOCUMENT,
    TEXT,
    FILE,
}

@Serializable
data class ArtifactRef(
    val id: String,
    val kind: ArtifactKind,
    val name: String,
    val path: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val createdAtMs: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
)
```

## 5. Seams

`local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactStore.kt`

```kotlin
/** Resolves artifact bytes. Pure seam; app implements via Koin DI. */
interface ArtifactResolver {
    suspend fun resolve(ref: ArtifactRef): ByteArray?
    suspend fun open(ref: ArtifactRef): InputStream?
}

/** Writes artifact bytes and returns a durable reference. Pure seam; app implements. */
interface ArtifactSink {
    suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef
}
```

## 6. ProcessReceipt change

`local-llm/.../litert/terminal/ProcessReceipt.kt`: add trailing optional field
`val outputRef: ArtifactRef? = null` (backward-compatible; existing
construction sites compile unchanged). Update the KDoc note: the `outputRef` is
now added. Raw output never lives in the receipt — it stays in scrollback or an
artifact.

## 7. Testing

- `ArtifactRefTest` (local-llm JVM): serialization round-trip (full + minimal
  fields, all kinds); `metadata` map round-trips.
- `ProcessReceiptTest` (local-llm JVM): round-trips with `outputRef` set and
  with null; existing tests stay green.
- No device test this phase.

## 8. Verification gate

```bash
./gradlew :local-llm:testDebugUnitTest --no-daemon
./gradlew :local-llm:lintDebug --no-daemon
```

## 9. Out of scope

- App-side `ArtifactResolver`/`ArtifactSink` Koin implementations.
- GenerationService orchestration (separate phase).
- Compute abstraction, Browser sessions, shadow workspace persistence.
- Wiring `outputRef` into app-side `GenerationReceipt`/`ImageTools` (the types
  stay app-local; a future phase can align them onto the canonical ref).
