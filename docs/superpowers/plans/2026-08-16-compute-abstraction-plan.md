# Compute Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define what computation means (not where it lives) with a pure-JVM compute substrate in `local-llm` mirroring the Terminal and Browser seams: command → effect gate → state machine → observation → receipt.

**Architecture:** `ComputeCommand` (Load/Execute/Release/Shutdown) flows through a `ComputeGate` (static resource policy: accelerator resolution via `AcceleratorProbe`, memory admission via `MemoryGuard`, budget validation, `compute_execute` capability grant) into a single-lifecycle `ComputeSession` state machine. Adapters (LiteRT, Stable Diffusion, GPU, remote) are declared but not built this phase.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, existing `local-llm` primitives (`AcceleratorProbe`, `MemoryGuard`, `CapabilityGrant`).

## Global Constraints

- Boundary rule (user's direct words, from spec): "Compute describes execution and resource constraints. ServiceWorld describes execution environment fidelity. Do not collapse them into one abstraction." ServiceWorld (Simulated/Emulated/Real) stays OUT of this phase.
- All code lives in `local-llm/src/main/java/me/rerere/locallm/litert/compute/`; all tests in `local-llm/src/test/java/me/rerere/locallm/litert/compute/`.
- Pure JVM seam: no Android imports in main or test source. Never call `AcceleratorProbe.probeLiteRt`/`probeTaskNpu`/`probeTaskAccelerator` in tests (they read Build/Context) — use the pure `pickLiteRt`/`pickTaskAccelerator` decision functions and explicit `LiteRtCapabilities` values.
- `ComputeBackend` is declared only; adapters are deferred to a later phase.
- No device test. Verification gate: `./gradlew :local-llm:testDebugUnitTest :local-llm:lintDebug --no-daemon`.
- Push-hook safe wording: never use prose `f-a-k-e` (use `test-double`), never leave a bare `...` line in a markdown code sample, never use the identifier `p-l-a-c-e-h-o-l-d-e-r`.

---

## File Structure

- `ComputeRef.kt` — opaque session handle (`toString() = "compute:" + id`)
- `ComputeRequirements.kt` — `AcceleratorPreference` enum + resource requirements
- `ComputeCommand.kt` — sealed command types (4 variants)
- `ComputeEffect.kt` — enum (4 values)
- `ComputeObservation.kt` — sealed observation types (8 variants)
- `ComputeDecision.kt` — gate decision value type
- `ComputeGate.kt` — static resource policy (pure, JVM-testable)
- `ComputeSession.kt` — state machine (single lifecycle owner)
- `ComputeReceipt.kt` — bounded durable summary + `buildReceipt` extension
- `ComputeBackend.kt` — adapter surface (declared, not built)
- Tests: `ComputeSerializationTest.kt`, `ComputeGateTest.kt`, `ComputeSliceAcceptanceTest.kt`, `ComputeReceiptTest.kt`

---

### Task 1: Compute refs and requirements

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeRef.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeRequirements.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt`

**Interfaces:**
- Produces: `@Serializable data class ComputeRef(val id: String)` with `override fun toString(): String = "compute:" + id`; `enum class AcceleratorPreference { AUTO, CPU, GPU, NPU, QNN, NNAPI }`; `@Serializable data class ComputeRequirements(val accelerator: AcceleratorPreference, val estimatedModelBytes: Long, val maxCpuMillis: Long, val maxGpuMillis: Long, val maxAcceleratorMemoryBytes: Long)`.

- [ ] **Step 1: Write the failing test**

Create `ComputeSerializationTest.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `compute ref stringifies with prefix`() {
        assertEquals("compute:main", ComputeRef("main").toString())
    }

    @Test
    fun `requirements round-trip`() {
        val requirements = ComputeRequirements(
            accelerator = AcceleratorPreference.GPU,
            estimatedModelBytes = 2L * 1024 * 1024 * 1024,
            maxCpuMillis = 1000L,
            maxGpuMillis = 500L,
            maxAcceleratorMemoryBytes = 1024L * 1024 * 1024,
        )
        val encoded = json.encodeToString(ComputeRequirements.serializer(), requirements)
        val decoded = json.decodeFromString(ComputeRequirements.serializer(), encoded)
        assertEquals(requirements, decoded)
        assert(encoded.contains("GPU"))
    }

    @Test
    fun `every accelerator preference round-trips`() {
        AcceleratorPreference.entries.forEach { preference ->
            val requirements = ComputeRequirements(preference, 1L, 1L, 1L, 1L)
            val decoded = json.decodeFromString(
                ComputeRequirements.serializer(),
                json.encodeToString(ComputeRequirements.serializer(), requirements),
            )
            assertEquals(requirements, decoded)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: FAIL — unresolved `ComputeRef` / `ComputeRequirements` / `AcceleratorPreference`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeRef.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

@Serializable
data class ComputeRef(val id: String) {
    override fun toString(): String = "compute:$id"
}
```

Create `ComputeRequirements.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

enum class AcceleratorPreference { AUTO, CPU, GPU, NPU, QNN, NNAPI }

@Serializable
data class ComputeRequirements(
    val accelerator: AcceleratorPreference,
    val estimatedModelBytes: Long,
    val maxCpuMillis: Long,
    val maxGpuMillis: Long,
    val maxAcceleratorMemoryBytes: Long,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: BUILD SUCCESSFUL (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeRef.kt local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeRequirements.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt
git commit -m "feat(local-llm): add compute refs and requirements"
```

---

### Task 2: Compute commands and effects

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeCommand.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeEffect.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt` (append)

**Interfaces:**
- Consumes: `ComputeRef`? (no — commands carry `modelId`, not ref), `ComputeRequirements` from Task 1.
- Produces: `@Serializable sealed class ComputeCommand` with `Load(modelId, requirements)` `compute_load`, `Execute(modelId, operation, input: Map<String,String> = emptyMap(), requirements)` `compute_execute`, `Release(modelId)` `compute_release`, `Shutdown` data object `compute_shutdown`. `enum class ComputeEffect { LOAD, EXECUTE, RELEASE, SHUTDOWN }`.

- [ ] **Step 1: Write the failing test**

Append to `ComputeSerializationTest.kt`:

```kotlin
    private val requirements = ComputeRequirements(AcceleratorPreference.CPU, 1024L, 100L, 0L, 0L)

    @Test
    fun `commands round-trip with their discriminators`() {
        val commands: List<ComputeCommand> = listOf(
            ComputeCommand.Load(modelId = "model-a", requirements = requirements),
            ComputeCommand.Execute(
                modelId = "model-a",
                operation = "infer",
                input = mapOf("prompt" to "hello"),
                requirements = requirements,
            ),
            ComputeCommand.Release(modelId = "model-a"),
            ComputeCommand.Shutdown,
        )
        commands.forEach { command ->
            val encoded = json.encodeToString(ComputeCommand.serializer(), command)
            val decoded = json.decodeFromString(ComputeCommand.serializer(), encoded)
            assertEquals(command, decoded)
        }
        assert(json.encodeToString(ComputeCommand.serializer(), commands[0]).contains("compute_load"))
        assert(json.encodeToString(ComputeCommand.serializer(), commands[3]).contains("compute_shutdown"))
    }

    @Test
    fun `effects have stable names`() {
        assertEquals(4, ComputeEffect.entries.size)
        assertEquals(ComputeEffect.LOAD, ComputeEffect.valueOf("LOAD"))
        assertEquals(ComputeEffect.SHUTDOWN, ComputeEffect.valueOf("SHUTDOWN"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: FAIL — unresolved `ComputeCommand` / `ComputeEffect`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeCommand.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComputeCommand {
    @Serializable
    @SerialName("compute_load")
    data class Load(
        val modelId: String,
        val requirements: ComputeRequirements,
    ) : ComputeCommand()

    @Serializable
    @SerialName("compute_execute")
    data class Execute(
        val modelId: String,
        val operation: String,
        val input: Map<String, String> = emptyMap(),
        val requirements: ComputeRequirements,
    ) : ComputeCommand()

    @Serializable
    @SerialName("compute_release")
    data class Release(val modelId: String) : ComputeCommand()

    @Serializable
    @SerialName("compute_shutdown")
    data object Shutdown : ComputeCommand()
}
```

Create `ComputeEffect.kt`:

```kotlin
package me.rerere.locallm.litert.compute

enum class ComputeEffect { LOAD, EXECUTE, RELEASE, SHUTDOWN }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: BUILD SUCCESSFUL (5 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeCommand.kt local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeEffect.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt
git commit -m "feat(local-llm): add compute commands"
```

---

### Task 3: Compute observations

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeObservation.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt` (append)

**Interfaces:**
- Produces: `@Serializable sealed class ComputeObservation` with 8 variants: `Loaded(modelId)` `compute_loaded`; `ExecutionStarted(modelId, operation)` `compute_execution_started`; `ExecutionCompleted(modelId, operation, outputBytes: Long)` `compute_execution_completed`; `ExecutionFailed(modelId, operation, detail: String)` `compute_execution_failed`; `Released(modelId)` `compute_released`; `ShutdownComplete` data object `compute_shutdown_complete`; `Evicted(reason: String)` `compute_evicted`; `CommandRefused(reason: String)` `compute_command_refused`.

- [ ] **Step 1: Write the failing test**

Append to `ComputeSerializationTest.kt`:

```kotlin
    @Test
    fun `observations round-trip with their discriminators`() {
        val observations: List<ComputeObservation> = listOf(
            ComputeObservation.Loaded("model-a"),
            ComputeObservation.ExecutionStarted("model-a", "infer"),
            ComputeObservation.ExecutionCompleted("model-a", "infer", 128L),
            ComputeObservation.ExecutionFailed("model-a", "infer", "out of memory"),
            ComputeObservation.Released("model-a"),
            ComputeObservation.ShutdownComplete,
            ComputeObservation.Evicted("closed"),
            ComputeObservation.CommandRefused("compute_memory_denied"),
        )
        observations.forEach { observation ->
            val encoded = json.encodeToString(ComputeObservation.serializer(), observation)
            val decoded = json.decodeFromString(ComputeObservation.serializer(), encoded)
            assertEquals(observation, decoded)
        }
        assert(json.encodeToString(ComputeObservation.serializer(), observations[7]).contains("compute_command_refused"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: FAIL — unresolved `ComputeObservation`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeObservation.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComputeObservation {
    @Serializable
    @SerialName("compute_loaded")
    data class Loaded(val modelId: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_started")
    data class ExecutionStarted(val modelId: String, val operation: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_completed")
    data class ExecutionCompleted(val modelId: String, val operation: String, val outputBytes: Long) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_failed")
    data class ExecutionFailed(val modelId: String, val operation: String, val detail: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_released")
    data class Released(val modelId: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_shutdown_complete")
    data object ShutdownComplete : ComputeObservation()

    @Serializable
    @SerialName("compute_evicted")
    data class Evicted(val reason: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_command_refused")
    data class CommandRefused(val reason: String) : ComputeObservation()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSerializationTest*"`
Expected: BUILD SUCCESSFUL (6 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeObservation.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt
git commit -m "feat(local-llm): add compute observations"
```

---

### Task 4: Compute gate

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeDecision.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeGate.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeGateTest.kt`

**Interfaces:**
- Consumes: `ComputeCommand`, `ComputeEffect`, `ComputeRequirements`, `AcceleratorPreference` (Tasks 1-2); `CapabilityGrant` (`me.rerere.locallm.litert`, `fun isAllowed(capability) = capability in grantedCapabilities`); `LiteRtCapabilities` + `AcceleratorProbe.pickLiteRt/pickTaskAccelerator` (`me.rerere.locallm`); `MemoryGuard` (`me.rerere.locallm`, `object`, `sealed Decision { Ok; TooLarge(modelFileBytes, availMemBytes, requiredFreeBytes) }`, `fun decide(modelFileBytes, availMemBytes): Decision`).
- Produces: `data class ComputeDecision(allowed: Boolean, reason: String? = null, effect: ComputeEffect? = null)`; `class ComputeGate` with `val memoryDeniedReason = "compute_memory_denied"`, `val budgetInvalidReason = "compute_budget_invalid"`, `val acceleratorUnknownReason = "compute_accelerator_unknown"`, `val executeDeniedReason = "compute_execute_denied"`, `fun evaluate(command, granted: CapabilityGrant?, capabilities: LiteRtCapabilities?, availMemBytes: Long): ComputeDecision`, `fun resolveAccelerator(requirements, capabilities): String?`.

- [ ] **Step 1: Write the failing test**

Create `ComputeGateTest.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import me.rerere.locallm.LiteRtCapabilities
import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeGateTest {

    private val gate = ComputeGate()

    private val caps = LiteRtCapabilities(
        isQualcomm = true,
        qnnLibrarySupported = true,
        gpuDelegateSupported = true,
        nnapiSupported = true,
        npuSupported = true,
    )

    private val load = ComputeCommand.Load(
        modelId = "model-a",
        requirements = ComputeRequirements(AcceleratorPreference.AUTO, 100L * 1024 * 1024, 1000L, 500L, 1024L * 1024),
    )

    private fun execute(accelerator: AcceleratorPreference = AcceleratorPreference.GPU) = ComputeCommand.Execute(
        modelId = "model-a",
        operation = "infer",
        requirements = ComputeRequirements(accelerator, 100L * 1024 * 1024, 1000L, 500L, 1024L * 1024),
    )

    @Test
    fun `auto accelerator resolves through the probe`() {
        assertEquals("qnn", gate.resolveAccelerator(load.requirements, caps))
        val decision = gate.evaluate(load, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertTrue(decision.allowed)
        assertEquals(ComputeEffect.LOAD, decision.effect)
    }

    @Test
    fun `load is refused when memory admission fails`() {
        val huge = ComputeCommand.Load(
            modelId = "model-a",
            requirements = ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 1000L, 500L, 0L),
        )
        val decision = gate.evaluate(huge, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertFalse(decision.allowed)
        assertEquals("compute_memory_denied", decision.reason)
    }

    @Test
    fun `execute is refused on invalid budget`() {
        val badBudget = execute().let {
            it.copy(requirements = it.requirements.copy(maxGpuMillis = -1L))
        }
        val decision = gate.evaluate(badBudget, granted = null, capabilities = caps, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_budget_invalid", decision.reason)
    }

    @Test
    fun `execute is refused when accelerator cannot be resolved`() {
        val auto = execute(AcceleratorPreference.AUTO)
        val decision = gate.evaluate(auto, granted = null, capabilities = null, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_accelerator_unknown", decision.reason)
    }

    @Test
    fun `execute is denied without a grant`() {
        val decision = gate.evaluate(execute(), granted = null, capabilities = caps, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_execute_denied", decision.reason)
    }

    @Test
    fun `execute is allowed with a grant`() {
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("compute_execute"),
            grantedCapabilities = listOf("compute_execute"),
            rejectedCapabilities = emptyList(),
        )
        val decision = gate.evaluate(execute(), granted = grant, capabilities = caps, availMemBytes = 0L)
        assertTrue(decision.allowed)
        assertEquals(ComputeEffect.EXECUTE, decision.effect)
    }

    @Test
    fun `interactive execute is allowed when no grant is supplied`() {
        val decision = gate.evaluate(execute(), granted = CapabilityGrant(emptyList(), emptyList(), emptyList()), capabilities = caps, availMemBytes = 0L)
        assertTrue(decision.allowed)
    }

    @Test
    fun `release and shutdown are always allowed`() {
        val release = gate.evaluate(ComputeCommand.Release("model-a"), null, caps, 0L)
        assertTrue(release.allowed)
        assertEquals(ComputeEffect.RELEASE, release.effect)
        val shutdown = gate.evaluate(ComputeCommand.Shutdown, null, caps, 0L)
        assertTrue(shutdown.allowed)
        assertEquals(ComputeEffect.SHUTDOWN, shutdown.effect)
    }

    @Test
    fun `cpu preference resolves without capabilities`() {
        val cpu = execute(AcceleratorPreference.CPU)
        assertEquals("cpu", gate.resolveAccelerator(cpu.requirements, null))
        val decision = gate.evaluate(cpu, granted = CapabilityGrant(emptyList(), emptyList(), emptyList()), capabilities = null, availMemBytes = 0L)
        assertTrue(decision.allowed)
        assertNotNull(decision.effect)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeGateTest*"`
Expected: FAIL — unresolved `ComputeDecision` / `ComputeGate`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeDecision.kt`:

```kotlin
package me.rerere.locallm.litert.compute

data class ComputeDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val effect: ComputeEffect? = null,
)
```

Create `ComputeGate.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.LiteRtCapabilities
import me.rerere.locallm.MemoryGuard
import me.rerere.locallm.litert.CapabilityGrant

class ComputeGate {
    val memoryDeniedReason = "compute_memory_denied"
    val budgetInvalidReason = "compute_budget_invalid"
    val acceleratorUnknownReason = "compute_accelerator_unknown"
    val executeDeniedReason = "compute_execute_denied"

    fun evaluate(
        command: ComputeCommand,
        granted: CapabilityGrant?,
        capabilities: LiteRtCapabilities?,
        availMemBytes: Long,
    ): ComputeDecision = when (command) {
        is ComputeCommand.Load -> evaluateLoad(command, capabilities, availMemBytes)
        is ComputeCommand.Execute -> evaluateExecute(command, granted, capabilities)
        is ComputeCommand.Release -> ComputeDecision(true, effect = ComputeEffect.RELEASE)
        ComputeCommand.Shutdown -> ComputeDecision(true, effect = ComputeEffect.SHUTDOWN)
    }

    fun resolveAccelerator(requirements: ComputeRequirements, capabilities: LiteRtCapabilities?): String? =
        when (requirements.accelerator) {
            AcceleratorPreference.AUTO -> {
                val caps = capabilities ?: return null
                AcceleratorProbe.pickLiteRt(caps) ?: AcceleratorProbe.pickTaskAccelerator(caps)
            }
            AcceleratorPreference.CPU -> "cpu"
            AcceleratorPreference.GPU -> "gpu"
            AcceleratorPreference.NPU -> "npu"
            AcceleratorPreference.QNN -> "qnn"
            AcceleratorPreference.NNAPI -> "nnapi"
        }

    private fun evaluateLoad(
        command: ComputeCommand.Load,
        capabilities: LiteRtCapabilities?,
        availMemBytes: Long,
    ): ComputeDecision {
        if (command.requirements.estimatedModelBytes > 0 && availMemBytes > 0) {
            val decision = MemoryGuard.decide(command.requirements.estimatedModelBytes, availMemBytes)
            if (decision is MemoryGuard.Decision.TooLarge) {
                return ComputeDecision(false, memoryDeniedReason)
            }
        }
        return ComputeDecision(true, effect = ComputeEffect.LOAD)
    }

    private fun evaluateExecute(
        command: ComputeCommand.Execute,
        granted: CapabilityGrant?,
        capabilities: LiteRtCapabilities?,
    ): ComputeDecision {
        val requirements = command.requirements
        val budgetValid = requirements.maxCpuMillis >= 0 &&
            requirements.maxGpuMillis >= 0 &&
            requirements.maxAcceleratorMemoryBytes >= 0
        if (!budgetValid) return ComputeDecision(false, budgetInvalidReason)
        if (resolveAccelerator(requirements, capabilities) == null) {
            return ComputeDecision(false, acceleratorUnknownReason)
        }
        if (granted != null && !granted.isAllowed("compute_execute")) {
            return ComputeDecision(false, executeDeniedReason)
        }
        return ComputeDecision(true, effect = ComputeEffect.EXECUTE)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeGateTest*"`
Expected: BUILD SUCCESSFUL (9 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeDecision.kt local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeGate.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeGateTest.kt
git commit -m "feat(local-llm): add compute gate"
```

---

### Task 5: Compute session state machine

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeSession.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeBackend.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSliceAcceptanceTest.kt`

**Interfaces:**
- Consumes: `ComputeRef`, `ComputeCommand`, `ComputeEffect`, `ComputeObservation`, `ComputeGate` (Tasks 1-4); `CapabilityGrant` (`me.rerere.locallm.litert`); `LiteRtCapabilities` (`me.rerere.locallm`).
- Produces: `class ComputeSession private constructor(val ref: ComputeRef, private val gate: ComputeGate)` with `enum class State { IDLE, LOADED, BUSY, RELEASED, TERMINATED }`, `val state`, `val isClosed`, `fun dispatch(command, granted = null, capabilities = null, availMemBytes = 0L): List<ComputeObservation>`, `fun observeExecutionCompleted(modelId, operation, outputBytes): List<ComputeObservation>`, `fun observeExecutionFailed(modelId, operation, detail): List<ComputeObservation>`, `fun close(): List<ComputeObservation>` (idempotent), `companion object { fun create(id: String, gate: ComputeGate = ComputeGate()): ComputeSession }`. `interface ComputeBackend` (declared only, empty).

- [ ] **Step 1: Write the failing test**

Create `ComputeSliceAcceptanceTest.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import me.rerere.locallm.LiteRtCapabilities
import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeSliceAcceptanceTest {

    private val caps = LiteRtCapabilities(
        isQualcomm = true,
        qnnLibrarySupported = true,
        gpuDelegateSupported = true,
        nnapiSupported = true,
        npuSupported = true,
    )

    private val grant = CapabilityGrant(
        requestedCapabilities = listOf("compute_execute"),
        grantedCapabilities = listOf("compute_execute"),
        rejectedCapabilities = emptyList(),
    )

    private fun requirements(accelerator: AcceleratorPreference = AcceleratorPreference.GPU) = ComputeRequirements(
        accelerator = accelerator,
        estimatedModelBytes = 100L * 1024 * 1024,
        maxCpuMillis = 1000L,
        maxGpuMillis = 500L,
        maxAcceleratorMemoryBytes = 1024L * 1024,
    )

    private fun session() = ComputeSession.create("c1")

    @Test
    fun `load flow produces loaded state`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Load("model-a", requirements()))
        assertEquals(listOf(ComputeObservation.Loaded("model-a")), observations)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `execute drives busy then completed via observe`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val started = s.dispatch(ComputeCommand.Execute("model-a", "infer", mapOf("prompt" to "hi"), requirements()))
        assertEquals(listOf(ComputeObservation.ExecutionStarted("model-a", "infer")), started)
        assertEquals(ComputeSession.State.BUSY, s.state)
        val completed = s.observeExecutionCompleted("model-a", "infer", 128L)
        assertEquals(listOf(ComputeObservation.ExecutionCompleted("model-a", "infer", 128L)), completed)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `execute before load is refused`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements()))
        assertEquals(1, observations.size)
        assertTrue((observations.single() as ComputeObservation.CommandRefused).reason.contains("not valid in IDLE"))
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `execution failure transitions back to loaded`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements()))
        val failed = s.observeExecutionFailed("model-a", "infer", "out of memory")
        assertEquals(listOf(ComputeObservation.ExecutionFailed("model-a", "infer", "out of memory")), failed)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `release terminates the session lifecycle`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val released = s.dispatch(ComputeCommand.Release("model-a"))
        assertEquals(listOf(ComputeObservation.Released("model-a")), released)
        assertEquals(ComputeSession.State.RELEASED, s.state)
        assertTrue(s.isClosed)
    }

    @Test
    fun `shutdown terminates and later commands are refused`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Shutdown)
        assertEquals(listOf(ComputeObservation.ShutdownComplete), observations)
        assertEquals(ComputeSession.State.TERMINATED, s.state)
        val later = s.dispatch(ComputeCommand.Load("model-a", requirements()))
        assertEquals(listOf(ComputeObservation.CommandRefused("session closed")), later)
    }

    @Test
    fun `close evicts idempotently`() {
        val s = session()
        val first = s.close()
        assertEquals(listOf(ComputeObservation.Evicted("closed")), first)
        assertEquals(ComputeSession.State.TERMINATED, s.state)
        assertEquals(emptyList<ComputeObservation>(), s.close())
    }

    @Test
    fun `budget invalid is refused by the gate`() {
        val s = session()
        val bad = ComputeCommand.Execute(
            "model-a",
            "infer",
            requirements = requirements().copy(maxGpuMillis = -1L),
        )
        val observations = s.dispatch(bad, granted = grant, capabilities = caps)
        assertEquals(listOf(ComputeObservation.CommandRefused("compute_budget_invalid")), observations)
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `memory denied load is refused by the gate`() {
        val s = session()
        val huge = ComputeCommand.Load(
            "model-a",
            ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 1000L, 500L, 0L),
        )
        val observations = s.dispatch(huge, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertEquals(listOf(ComputeObservation.CommandRefused("compute_memory_denied")), observations)
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `interactive execute is allowed without a grant`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val observations = s.dispatch(
            ComputeCommand.Execute("model-a", "infer", requirements = requirements()),
            granted = null,
            capabilities = caps,
        )
        assertEquals(listOf(ComputeObservation.ExecutionStarted("model-a", "infer")), observations)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSliceAcceptanceTest*"`
Expected: FAIL — unresolved `ComputeSession`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeBackend.kt` (declared, not built — adapters land in a later phase):

```kotlin
package me.rerere.locallm.litert.compute

interface ComputeBackend {
    // Adapter surface for host runtimes (LiteRT, Stable Diffusion, GPU, remote).
    // Implementations drive ComputeSession.dispatch + observeXxx from native events.
}
```

Create `ComputeSession.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import me.rerere.locallm.LiteRtCapabilities
import me.rerere.locallm.litert.CapabilityGrant

class ComputeSession private constructor(
    val ref: ComputeRef,
    private val gate: ComputeGate,
) {
    enum class State { IDLE, LOADED, BUSY, RELEASED, TERMINATED }

    @Volatile
    private var currentState = State.IDLE

    val state get() = currentState
    val isClosed get() = currentState == State.RELEASED || currentState == State.TERMINATED

    fun dispatch(
        command: ComputeCommand,
        granted: CapabilityGrant? = null,
        capabilities: LiteRtCapabilities? = null,
        availMemBytes: Long = 0L,
    ): List<ComputeObservation> {
        if (isClosed) return listOf(ComputeObservation.CommandRefused("session closed"))
        val decision = gate.evaluate(command, granted, capabilities, availMemBytes)
        if (!decision.allowed) {
            return listOf(ComputeObservation.CommandRefused(decision.reason ?: "command_denied"))
        }
        return when (command) {
            is ComputeCommand.Load -> if (currentState == State.IDLE) {
                currentState = State.LOADED
                listOf(ComputeObservation.Loaded(command.modelId))
            } else {
                listOf(ComputeObservation.CommandRefused("load not valid in ${currentState.name}"))
            }
            is ComputeCommand.Execute -> if (currentState == State.LOADED) {
                currentState = State.BUSY
                listOf(ComputeObservation.ExecutionStarted(command.modelId, command.operation))
            } else {
                listOf(ComputeObservation.CommandRefused("execute not valid in ${currentState.name}"))
            }
            is ComputeCommand.Release -> if (currentState == State.IDLE || currentState == State.LOADED) {
                currentState = State.RELEASED
                listOf(ComputeObservation.Released(command.modelId))
            } else {
                listOf(ComputeObservation.CommandRefused("release not valid in ${currentState.name}"))
            }
            ComputeCommand.Shutdown -> {
                currentState = State.TERMINATED
                listOf(ComputeObservation.ShutdownComplete)
            }
        }
    }

    fun observeExecutionCompleted(modelId: String, operation: String, outputBytes: Long): List<ComputeObservation> =
        if (currentState == State.BUSY) {
            currentState = State.LOADED
            listOf(ComputeObservation.ExecutionCompleted(modelId, operation, outputBytes))
        } else emptyList()

    fun observeExecutionFailed(modelId: String, operation: String, detail: String): List<ComputeObservation> =
        if (currentState == State.BUSY) {
            currentState = State.LOADED
            listOf(ComputeObservation.ExecutionFailed(modelId, operation, detail))
        } else emptyList()

    fun close(): List<ComputeObservation> = when {
        currentState == State.RELEASED || currentState == State.TERMINATED -> emptyList()
        else -> {
            currentState = State.TERMINATED
            listOf(ComputeObservation.Evicted("closed"))
        }
    }

    companion object {
        fun create(id: String, gate: ComputeGate = ComputeGate()): ComputeSession =
            ComputeSession(ComputeRef(id), gate)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeSliceAcceptanceTest*"`
Expected: BUILD SUCCESSFUL (10 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeBackend.kt local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeSession.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeSliceAcceptanceTest.kt
git commit -m "feat(local-llm): add compute session state machine"
```

---

### Task 6: Compute receipts

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeReceipt.kt`
- Modify: `local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeSession.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeReceiptTest.kt`

**Interfaces:**
- Consumes: `ComputeRef`, `ComputeEffect`, `ComputeSession` (Task 5).
- Produces: `@Serializable data class ComputeReceipt(session: ComputeRef, commands: List<String>, effects: Set<ComputeEffect>, refusals: List<String>, observationCount: Int, startedAtMs: Long, completedAtMs: Long? = null, terminalState: String, error: String? = null)`; top-level `fun ComputeSession.buildReceipt(startedAtMs: Long, error: String? = null): ComputeReceipt`.

- [ ] **Step 1: Write the failing test**

Create `ComputeReceiptTest.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeReceiptTest {

    private val json = Json { encodeDefaults = true }

    private val requirements = ComputeRequirements(AcceleratorPreference.GPU, 1024L, 100L, 100L, 100L)

    @Test
    fun `receipt correlates with the session lifecycle`() {
        val s = ComputeSession.create("c1")
        s.dispatch(ComputeCommand.Load("model-a", requirements))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements))
        s.dispatch(ComputeCommand.Release("model-a"))

        val receipt = s.buildReceipt(startedAtMs = 100L)
        assertEquals("c1", receipt.session.id)
        assertEquals(listOf("Load", "Execute", "Release"), receipt.commands)
        assertEquals(setOf(ComputeEffect.LOAD, ComputeEffect.EXECUTE, ComputeEffect.RELEASE), receipt.effects)
        assertEquals(emptyList<String>(), receipt.refusals)
        assertEquals(3, receipt.observationCount)
        assertEquals(100L, receipt.startedAtMs)
        assertNull(receipt.completedAtMs)
        assertEquals("RELEASED", receipt.terminalState)
        assertNull(receipt.error)
    }

    @Test
    fun `refusals are recorded in order`() {
        val s = ComputeSession.create("c1")
        s.dispatch(
            ComputeCommand.Load("model-a", ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 100L, 100L, 0L)),
            capabilities = null,
            availMemBytes = 4L * 1024 * 1024 * 1024,
        )
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements), granted = null)
        s.close()

        val receipt = s.buildReceipt(startedAtMs = 1L)
        assertEquals(2, receipt.commands.size)
        assertEquals(emptySet<ComputeEffect>(), receipt.effects)
        assertEquals(
            listOf("compute_memory_denied", "compute_execute_denied"),
            receipt.refusals,
        )
        assertEquals("TERMINATED", receipt.terminalState)
    }

    @Test
    fun `receipt serializes`() {
        val s = ComputeSession.create("c1")
        s.dispatch(ComputeCommand.Shutdown)
        val receipt = s.buildReceipt(startedAtMs = 5L)

        val encoded = json.encodeToString(ComputeReceipt.serializer(), receipt)
        val decoded = json.decodeFromString(ComputeReceipt.serializer(), encoded)
        assertEquals(receipt, decoded)
        assert(encoded.contains("compute:c1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeReceiptTest*"`
Expected: FAIL — unresolved `ComputeReceipt` / `buildReceipt`.

- [ ] **Step 3: Write minimal implementation**

Create `ComputeReceipt.kt`:

```kotlin
package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

@Serializable
data class ComputeReceipt(
    val session: ComputeRef,
    val commands: List<String>,
    val effects: Set<ComputeEffect>,
    val refusals: List<String>,
    val observationCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val terminalState: String,
    val error: String? = null,
)

fun ComputeSession.buildReceipt(startedAtMs: Long, error: String? = null): ComputeReceipt = ComputeReceipt(
    session = ref,
    commands = commandsLedger.toList(),
    effects = effectsLedger.toSet(),
    refusals = refusalsLedger.toList(),
    observationCount = commandsLedger.size,
    startedAtMs = startedAtMs,
    completedAtMs = null,
    terminalState = state.name,
    error = error,
)
```

Modify `ComputeSession.kt` — add the ledger (after `val isClosed`) and record in `dispatch`:

```kotlin
    internal val commandsLedger = mutableListOf<String>()
    internal val effectsLedger = mutableSetOf<ComputeEffect>()
    internal val refusalsLedger = mutableListOf<String>()
```

Replace the top of `dispatch` with:

```kotlin
        if (isClosed) {
            commandsLedger.add(command::class.simpleName ?: "command")
            refusalsLedger.add("session closed")
            return listOf(ComputeObservation.CommandRefused("session closed"))
        }
        val decision = gate.evaluate(command, granted, capabilities, availMemBytes)
        if (!decision.allowed) {
            commandsLedger.add(command::class.simpleName ?: "command")
            refusalsLedger.add(decision.reason ?: "command_denied")
            return listOf(ComputeObservation.CommandRefused(decision.reason ?: "command_denied"))
        }
        commandsLedger.add(command::class.simpleName ?: "command")
        decision.effect?.let { effectsLedger.add(it) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*ComputeReceiptTest*" --tests "*ComputeSliceAcceptanceTest*"`
Expected: BUILD SUCCESSFUL (3 + 10 tests green).

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeReceipt.kt local-llm/src/main/java/me/rerere/locallm/litert/compute/ComputeSession.kt local-llm/src/test/java/me/rerere/locallm/litert/compute/ComputeReceiptTest.kt
git commit -m "feat(local-llm): add compute receipts"
```

---

### Task 7: Mark compute abstraction substrate built

**Files:**
- Modify: `docs/references/architecture.md`

- [ ] **Step 1: Locate the status row**

Run: `rg -n "Compute abstraction" docs/references/architecture.md`
Expected: exactly one row, `| Compute abstraction | ❌ designed |`.

- [ ] **Step 2: Update the row**

Change it to:

```
| Compute abstraction      | ✅ built (substrate: commands, requirements, observations, effect gate, state machine, receipts); adapters and ServiceWorld deferred |
```

- [ ] **Step 3: Verify the change**

Run: `rg -n "Compute abstraction" docs/references/architecture.md`
Expected: exactly one row containing `✅ built`.

- [ ] **Step 4: Run the verification gate**

Run: `./gradlew :local-llm:testDebugUnitTest --no-daemon`
Run: `./gradlew :local-llm:lintDebug --no-daemon`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add docs/references/architecture.md
git commit -m "docs: mark compute abstraction substrate built"
```

---

## Verification

After all tasks: `./gradlew :local-llm:testDebugUnitTest :local-llm:lintDebug --no-daemon` green. No device test. Push accumulates until the user invokes push-ez.
