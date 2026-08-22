# ArtifactRef Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the canonical `ArtifactRef` type plus read/write seams in local-llm and hook `ProcessReceipt.outputRef` to it.

**Architecture:** A pure JVM seam in local-llm (so `ProcessReceipt` in `litert/terminal` can adopt it without any app dependency). `ArtifactRef` is a `@Serializable` data class with a kind enum; read and write are two narrow interfaces (`ArtifactResolver` / `ArtifactSink`) mirroring the `CapabilityGrantSource` seam pattern. The app implements the adapters via Koin DI in a later phase.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Gradle (`local-llm` module).

## Global Constraints

- `ArtifactRef` lives in `local-llm/src/main/java/me/rerere/locallm/litert/artifact/` — the module that `ProcessReceipt` already lives in.
- `ArtifactKind` enum values (exact, order): `PROCESS_OUTPUT`, `IMAGE`, `DOCUMENT`, `TEXT`, `FILE`.
- `ArtifactRef` field order (exact): `id`, `kind`, `name`, then nullable `path`, `uri`, `mimeType`, `byteSize`, `createdAtMs`, then `metadata: Map<String,String> = emptyMap()`.
- `ProcessReceipt` gains a trailing optional `val outputRef: ArtifactRef? = null` — existing construction sites compile unchanged.
- Verification gate after every task: `./gradlew :local-llm:testDebugUnitTest --no-daemon` then `./gradlew :local-llm:lintDebug --no-daemon`, both BUILD SUCCESSFUL.
- No device test in this phase. Never run `connectedDebugAndroidTest` against a phone with real data.
- Push hook false-positives to avoid in code/comments/docs: prose `f-a-k-e`, a bare `...` line in a markdown code sample, and the identifier `p-l-a-c-e-h-o-l-d-e-r`.

---

### Task 1: Add ArtifactRef and ArtifactKind

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactRef.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactRefTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class ArtifactKind { PROCESS_OUTPUT, IMAGE, DOCUMENT, TEXT, FILE }` and `@Serializable data class ArtifactRef(id: String, kind: ArtifactKind, name: String, path: String? = null, uri: String? = null, mimeType: String? = null, byteSize: Long? = null, createdAtMs: Long? = null, metadata: Map<String, String> = emptyMap())` — both in package `me.rerere.locallm.litert.artifact`. Task 2's `ArtifactResolver`/`ArtifactSink` and Task 3's `ProcessReceipt.outputRef` consume these.

- [ ] **Step 1: Write the failing test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactRefTest.kt`:

```kotlin
package me.rerere.locallm.litert.artifact

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactRefTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `serialization round-trips a fully populated ref`() {
        val ref = ArtifactRef(
            id = "a-1",
            kind = ArtifactKind.PROCESS_OUTPUT,
            name = "build.log",
            path = "/data/user/0/app/files/artifacts/a-1/build.log",
            uri = "file:///data/user/0/app/files/artifacts/a-1/build.log",
            mimeType = "text/plain",
            byteSize = 1234L,
            createdAtMs = 1700000000000L,
            metadata = mapOf("generated_by" to "compile"),
        )

        val encoded = json.encodeToString(ArtifactRef.serializer(), ref)
        val decoded = json.decodeFromString(ArtifactRef.serializer(), encoded)

        assertEquals(ref, decoded)
    }

    @Test
    fun `serialization round-trips a minimal ref`() {
        val ref = ArtifactRef(id = "a-2", kind = ArtifactKind.TEXT, name = "note")

        val encoded = json.encodeToString(ArtifactRef.serializer(), ref)
        val decoded = json.decodeFromString(ArtifactRef.serializer(), encoded)

        assertEquals(ref, decoded)
    }

    @Test
    fun `nullable fields deserialize as null when absent`() {
        val jsonText = """{"id":"a-3","kind":"IMAGE","name":"shot.png"}"""
        val decoded = json.decodeFromString(ArtifactRef.serializer(), jsonText)

        assertEquals(ArtifactRef(id = "a-3", kind = ArtifactKind.IMAGE, name = "shot.png"), decoded)
        assertNull(decoded.path)
        assertNull(decoded.uri)
        assertNull(decoded.byteSize)
    }

    @Test
    fun `every kind round-trips with its discriminator`() {
        for (kind in ArtifactKind.entries) {
            val ref = ArtifactRef(id = "k-${kind.name}", kind = kind, name = "item")
            val decoded = json.decodeFromString(ArtifactRef.serializer(), json.encodeToString(ArtifactRef.serializer(), ref))
            assertEquals(ref, decoded)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ArtifactRefTest*" --no-daemon`
Expected: BUILD FAILED — compilation error, `ArtifactRef` / `ArtifactKind` unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactRef.kt`:

```kotlin
package me.rerere.locallm.litert.artifact

import kotlinx.serialization.Serializable

/** The kinds of artifacts the agent can produce and later reference. */
@Serializable
enum class ArtifactKind {
    PROCESS_OUTPUT,
    IMAGE,
    DOCUMENT,
    TEXT,
    FILE,
}

/**
 * Canonical reference to an agent-produced artifact.
 *
 * Points to a named artifact with an optional filesystem path, content URI, mime type,
 * byte size, creation time and free-form metadata. Raw bytes never live on the ref — they
 * stay in the backing store and are reached through [ArtifactResolver].
 */
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

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ArtifactRefTest*" --no-daemon`
Expected: BUILD SUCCESSFUL — all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactRef.kt local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactRefTest.kt
git commit -m "feat(local-llm): add canonical artifact reference type"
```

---

### Task 2: Add ArtifactResolver and ArtifactSink seams

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactStore.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactStoreTest.kt`

**Interfaces:**
- Consumes: `ArtifactRef`, `ArtifactKind` from Task 1 (package `me.rerere.locallm.litert.artifact`).
- Produces: `interface ArtifactResolver { suspend fun resolve(ref: ArtifactRef): ByteArray?; suspend fun open(ref: ArtifactRef): java.io.InputStream? }` and `interface ArtifactSink { suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef }` — both in package `me.rerere.locallm.litert.artifact`. The app implements both via Koin DI in a later phase; nothing in this phase depends on them.

- [ ] **Step 1: Write the failing test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactStoreTest.kt`:

```kotlin
package me.rerere.locallm.litert.artifact

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactStoreTest {

    private class InMemoryStore : ArtifactSink, ArtifactResolver {
        val map = mutableMapOf<String, ByteArray>()
        override suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef {
            val ref = ArtifactRef(id = "stored-${map.size + 1}", kind = kind, name = name, mimeType = mimeType, byteSize = bytes.size.toLong())
            map[ref.id] = bytes
            return ref
        }

        override suspend fun resolve(ref: ArtifactRef): ByteArray? = map[ref.id]

        override suspend fun open(ref: ArtifactRef): java.io.InputStream? =
            map[ref.id]?.inputStream()
    }

    @Test
    fun `sink write produces a resolvable ref`() = kotlinx.coroutines.runBlocking {
        val store = InMemoryStore()
        val ref = store.write(ArtifactKind.PROCESS_OUTPUT, "out.log", "text/plain", byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), store.resolve(ref))
    }

    @Test
    fun `open returns a stream over the stored bytes`() = kotlinx.coroutines.runBlocking {
        val store = InMemoryStore()
        val ref = store.write(ArtifactKind.TEXT, "note.txt", null, byteArrayOf(9, 8, 7))

        val stream = store.open(ref)
        assertArrayEquals(byteArrayOf(9, 8, 7), stream!!.readBytes())
    }

    @Test
    fun `resolve returns null for unknown refs`() = kotlinx.coroutines.runBlocking {
        val store = InMemoryStore()
        assertNull(store.resolve(ArtifactRef(id = "missing", kind = ArtifactKind.FILE, name = "nope")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ArtifactStoreTest*" --no-daemon`
Expected: BUILD FAILED — `ArtifactSink` / `ArtifactResolver` unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactStore.kt`:

```kotlin
package me.rerere.locallm.litert.artifact

import java.io.InputStream

/**
 * Read-side seam for agent artifacts. Implemented app-side via Koin DI
 * (mirrors the CapabilityGrantSource seam pattern).
 */
interface ArtifactResolver {
    /** Returns the artifact bytes, or null when the artifact no longer exists. */
    suspend fun resolve(ref: ArtifactRef): ByteArray?

    /** Returns a stream over the artifact bytes, or null when it no longer exists. */
    suspend fun open(ref: ArtifactRef): InputStream?
}

/**
 * Write-side seam for agent artifacts. Implemented app-side via Koin DI.
 */
interface ArtifactSink {
    /** Persists [bytes] and returns the canonical ref to it. */
    suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ArtifactStoreTest*" --no-daemon`
Expected: BUILD SUCCESSFUL — all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/artifact/ArtifactStore.kt local-llm/src/test/java/me/rerere/locallm/litert/artifact/ArtifactStoreTest.kt
git commit -m "feat(local-llm): add artifact resolver and sink seams"
```

---

### Task 3: Hook ProcessReceipt to the canonical ArtifactRef

**Files:**
- Modify: `local-llm/src/main/java/me/rerere/locallm/litert/terminal/ProcessReceipt.kt:1-28`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/terminal/ProcessReceiptTest.kt:8-49`

**Interfaces:**
- Consumes: `ArtifactRef` from Task 1 (import `me.rerere.locallm.litert.artifact.ArtifactRef`).
- Produces: `ProcessReceipt` now ends with `val outputRef: ArtifactRef? = null` — existing construction sites compile unchanged; Task 3's test verifies round-trips with and without `outputRef`.

- [ ] **Step 1: Write the failing test**

Add these two tests to the existing `ProcessReceiptTest` class (after the `serialization round-trips` test):

```kotlin
    @Test
    fun `serialization round-trips with an output ref`() {
        val r = receipt().copy(
            outputRef = ArtifactRef(id = "o-1", kind = ArtifactKind.PROCESS_OUTPUT, name = "out.log"),
        )
        val encoded = json.encodeToString(ProcessReceipt.serializer(), r)
        val decoded = json.decodeFromString(ProcessReceipt.serializer(), encoded)

        assertEquals(r, decoded)
    }

    @Test
    fun `serialization round-trips with a null output ref`() {
        val r = receipt().copy(outputRef = null)
        val encoded = json.encodeToString(ProcessReceipt.serializer(), r)
        val decoded = json.decodeFromString(ProcessReceipt.serializer(), encoded)

        assertEquals(r, decoded)
        assertNull(decoded.outputRef)
    }
```

Add the imports `me.rerere.locallm.litert.artifact.ArtifactKind` and `me.rerere.locallm.litert.artifact.ArtifactRef` at the top, and `assertNull` to the existing JUnit imports (line 5 area).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ProcessReceiptTest*" --no-daemon`
Expected: BUILD FAILED — `outputRef` unresolved on `ProcessReceipt`.

- [ ] **Step 3: Write the minimal implementation**

Modify `ProcessReceipt.kt`:

- Add the import after the existing kotlinx.serialization import:
  `import me.rerere.locallm.litert.artifact.ArtifactRef`
- Replace the KDoc sentence `An `outputRef` is added when the canonical ArtifactRef lands (roadmap H).` with:

```kotlin
 * An [outputRef] to the canonical artifact holding this process's captured output,
 * when one was persisted. Raw output never lives on the receipt.
```

- Add the trailing field (before the closing `)`):

```kotlin
    val outputRef: ArtifactRef? = null,
```

The data class becomes:

```kotlin
@Serializable
data class ProcessReceipt(
    val process: ProcessRef,
    val command: List<String>,
    val commandDigest: String,
    val effects: Set<String>,
    val reads: List<String>,
    val writes: List<String>,
    val network: Boolean,
    val nativeExecution: Boolean,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val exitCode: Int,
    val termination: String,
    val outputBytes: Long,
    val outputTruncated: Boolean,
    val outputRef: ArtifactRef? = null,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ProcessReceiptTest*" --no-daemon`
Expected: BUILD SUCCESSFUL — all tests pass (existing 2 + new 2).

- [ ] **Step 5: Run the full local-llm suite and lint**

```bash
./gradlew :local-llm:testDebugUnitTest --no-daemon
./gradlew :local-llm:lintDebug --no-daemon
```

Both expected BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/terminal/ProcessReceipt.kt local-llm/src/test/java/me/rerere/locallm/litert/terminal/ProcessReceiptTest.kt
git commit -m "feat(local-llm): hook process receipts to canonical artifact refs"
```
