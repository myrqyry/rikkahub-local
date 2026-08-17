# Compute Abstraction Design

> **Status:** approved
> **Date:** 2026-08-16
> **Roadmap:** architecture.md line 159 `| Compute abstraction | ❌ designed |` → this design builds it (substrate).

## Background

The roadmap diagram (architecture.md lines 37-55) shows the Zero→Procedure branch feeding **Compute (LiteRT / CPU / GPU)** alongside ServiceWorld (Fake / Emulated / Real), both converging on WorkflowReceipt → AgentRun Event Trace. Compute abstraction is DESIGNED-ONLY — no code exists yet.

The app already ships real compute primitives in `local-llm`: `AcceleratorProbe` (accelerator selection decisions), `MemoryGuard` (memory admission), `ResourceBudget`/`ComputeBudget`/`ComputeUsage` (op-level resource policy), and `LiteRtRuntime` (the 966-line LLM runtime host). They work, but there is no portable *contract* for what a compute execution is — exactly the gap Terminal and Browser sessions closed before them.

Phase J established the pattern: `command → state machine → effect → observation → receipt`, pure-JVM in `local-llm`, with the real Android implementation becoming an *adapter* later. Phase K (Compute abstraction) applies the same pattern to computation.

## Scope

**In scope (this phase):** a pure-JVM compute substrate in `local-llm/.../litert/compute/` mirroring `terminal/` and `browser/` 1:1 — `ComputeRef`, `ComputeRequirements`, `ComputeCommand`, `ComputeEffect`, `ComputeObservation`, `ComputeDecision`, `ComputeGate`, `ComputeSession`, `ComputeReceipt`, plus JVM tests and the architecture.md status-table update. The `ComputeBackend` interface is *declared* but not implemented.

**Out of scope (deferred):** concrete adapters (LiteRT / CPU / GPU / Stable Diffusion / remote backends), **ServiceWorld**, litert-playground web host, app-side wiring.

## Boundary Rule

> **Compute describes execution and resource constraints. ServiceWorld describes execution environment fidelity. Do not collapse them into one abstraction.**

ServiceWorld (Fake / Emulated / Real) answers "what kind of environment is executing it" — a different question from "what computation is running and what resources does it need". It stays a separate roadmap item.

## Core Types

All `@Serializable` unless noted. Package `me.rerere.locallm.litert.compute`.

### ComputeRef

```kotlin
@Serializable
data class ComputeRef(val id: String) {
    override fun toString(): String = "compute:$id"
}
```

### AcceleratorPreference

```kotlin
enum class AcceleratorPreference { AUTO, CPU, GPU, NPU, QNN, NNAPI }
```

### ComputeRequirements

Mirrors the relevant `ComputeBudget` fields plus accelerator preference and model size (for admission).

```kotlin
@Serializable
data class ComputeRequirements(
    val accelerator: AcceleratorPreference,
    val estimatedModelBytes: Long,
    val maxCpuMillis: Long,
    val maxGpuMillis: Long,
    val maxAcceleratorMemoryBytes: Long,
)
```

### ComputeCommand

```kotlin
@Serializable
sealed class ComputeCommand {
    @Serializable @SerialName("compute_load")
    data class Load(val modelId: String, val requirements: ComputeRequirements) : ComputeCommand()

    @Serializable @SerialName("compute_execute")
    data class Execute(
        val modelId: String,
        val operation: String,
        val input: Map<String, String> = emptyMap(),
        val requirements: ComputeRequirements,
    ) : ComputeCommand()

    @Serializable @SerialName("compute_release")
    data class Release(val modelId: String) : ComputeCommand()

    @Serializable @SerialName("compute_shutdown")
    data object Shutdown : ComputeCommand()
}
```

`operation` is `"infer"` | `"generate"` by convention; adapters map it to native calls.

### ComputeEffect

```kotlin
enum class ComputeEffect { LOAD, EXECUTE, RELEASE, SHUTDOWN }
```

### ComputeObservation

```kotlin
@Serializable
sealed class ComputeObservation {
    @Serializable @SerialName("compute_loaded")
    data class Loaded(val modelId: String) : ComputeObservation()

    @Serializable @SerialName("compute_execution_started")
    data class ExecutionStarted(val modelId: String, val operation: String) : ComputeObservation()

    @Serializable @SerialName("compute_execution_completed")
    data class ExecutionCompleted(val modelId: String, val operation: String, val outputBytes: Long) : ComputeObservation()

    @Serializable @SerialName("compute_execution_failed")
    data class ExecutionFailed(val modelId: String, val operation: String, val detail: String) : ComputeObservation()

    @Serializable @SerialName("compute_released")
    data class Released(val modelId: String) : ComputeObservation()

    @Serializable @SerialName("compute_shutdown_complete")
    data object ShutdownComplete : ComputeObservation()

    @Serializable @SerialName("compute_evicted")
    data class Evicted(val reason: String) : ComputeObservation()

    @Serializable @SerialName("compute_command_refused")
    data class CommandRefused(val reason: String) : ComputeObservation()
}
```

### ComputeDecision

```kotlin
data class ComputeDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val effect: ComputeEffect? = null,
)
```

## ComputeGate

Static policy, pure, JVM-testable. Mirrors `ProcessGate`/`BrowserGate`.

```kotlin
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
    ): ComputeDecision
}
```

Rules:

- **AUTO accelerator** resolves via `AcceleratorProbe.pickLiteRt`/`pickTaskAccelerator` (pure decision functions; `capabilities` may be null → treat as unknown).
- **Load** requires memory admission via a `MemoryGuard.decide`-style check on `estimatedModelBytes` (0 or unknown → allowed, admission deferred to the backend).
- **Execute** requires valid non-negative budget fields and a known accelerator value.
- **Execute** additionally requires `"compute_execute"` granted when a grant is present (interactive-allowed otherwise).
- **Release / Shutdown** always allowed.

Refusal reasons are stable machine-readable codes: `compute_memory_denied`, `compute_budget_invalid`, `compute_accelerator_unknown`, `compute_execute_denied`.

## ComputeSession

Pure deterministic state machine, single lifecycle owner, no backend inside (mirrors `BrowserSession`).

```kotlin
class ComputeSession private constructor(
    val ref: ComputeRef,
    private val gate: ComputeGate,
) {
    enum class State { IDLE, LOADED, BUSY, RELEASED, TERMINATED }
    val state: State
    val isClosed: Boolean  // RELEASED || TERMINATED

    fun dispatch(
        command: ComputeCommand,
        granted: CapabilityGrant? = null,
        capabilities: LiteRtCapabilities? = null,
        availMemBytes: Long = 0L,
    ): List<ComputeObservation>

    fun observeExecutionCompleted(modelId: String, operation: String, outputBytes: Long): List<ComputeObservation>
    fun observeExecutionFailed(modelId: String, operation: String, detail: String): List<ComputeObservation>
    fun close(): List<ComputeObservation>

    companion object {
        fun create(id: String, gate: ComputeGate = ComputeGate()): ComputeSession
    }
}
```

Transitions:

| Command | Valid in | Result |
|---|---|---|
| `Load` | IDLE | LOADED + `Loaded` |
| `Execute` | LOADED | BUSY + `ExecutionStarted` |
| `Release` | IDLE, LOADED | RELEASED + `Released` |
| `Shutdown` | any | TERMINATED + `ShutdownComplete` |
| any | closed | `CommandRefused("session closed")` |
| any (gate refusal) | any | `CommandRefused(reason)` |
| any other mismatch | any | `CommandRefused("<SimpleName> not valid in <STATE>")` |

Completion paths (driven by adapters after a real execution):

- `observeExecutionCompleted` — BUSY only → LOADED + `ExecutionCompleted(modelId, operation, outputBytes)`; else `emptyList`.
- `observeExecutionFailed` — BUSY only → LOADED + `ExecutionFailed(modelId, operation, detail)`; else `emptyList`.
- `close()` — idempotent: RELEASED or TERMINATED → `emptyList`; else TERMINATED + `Evicted("closed")`.

`LiteRtRuntime`/`StableDiffusion` become adapters driving `dispatch` + `observe*` later; they are not built this phase.

## ComputeBackend

Declared now, implemented by adapters later:

```kotlin
interface ComputeBackend {
    suspend fun execute(command: ComputeCommand): List<ComputeObservation>
}
```

Not built this phase.

## ComputeReceipt

Exact mirror of `BrowserReceipt`:

```kotlin
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

fun ComputeSession.buildReceipt(startedAtMs: Long, error: String? = null): ComputeReceipt
```

The session keeps an internal ledger (`ledgerCommands: MutableList<String>`, `ledgerEffects: MutableSet<ComputeEffect>`, `ledgerRefusals: MutableList<String>`). `dispatch` records: closed → simpleName + refusal reason; gate-denied → simpleName + reason; allowed → simpleName + effect. `buildReceipt` reads the ledger (`commands = ledgerCommands.toList()`, `effects = ledgerEffects.toSet()`, `refusals = ledgerRefusals.toList()`, `observationCount = ledgerCommands.size`, `terminalState = state.name`, `completedAtMs = null`).

## Files

Create in `local-llm`:

- `src/main/java/me/rerere/locallm/litert/compute/ComputeRef.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeRequirements.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeCommand.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeObservation.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeGate.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeSession.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeReceipt.kt`
- `src/main/java/me/rerere/locallm/litert/compute/ComputeBackend.kt`
- `src/test/java/me/rerere/locallm/litert/compute/ComputeSerializationTest.kt`
- `src/test/java/me/rerere/locallm/litert/compute/ComputeGateTest.kt`
- `src/test/java/me/rerere/locallm/litert/compute/ComputeSliceAcceptanceTest.kt`
- `src/test/java/me/rerere/locallm/litert/compute/ComputeReceiptTest.kt`

Modify:

- `docs/references/architecture.md` — status row `| Compute abstraction | ❌ designed |` → `| Compute abstraction | ✅ built (substrate: commands, gate, state machine, observations, receipts); adapters and ServiceWorld deferred |`.

## Testing

JVM only, no device test:

- **ComputeSerializationTest** — every command and observation round-trips with its discriminator; `ComputeRequirements` and `ComputeReceipt` round-trip.
- **ComputeGateTest** — AUTO accelerator resolution; memory admission granted/denied; budget-invalid refused; unknown-accelerator refused; execute denied without grant; execute allowed with `CapabilityGrant(requested=[compute_execute], granted=[compute_execute], rejected=[])`; Release/Shutdown always allowed.
- **ComputeSliceAcceptanceTest** — load flow → LOADED; execute → BUSY then completed via observe; execute in IDLE refused; execute failure → LOADED; release; shutdown terminates + later `CommandRefused("session closed")`; close evicts idempotently; budget-invalid gate refused; memory-denied load refused.
- **ComputeReceiptTest** — correlation (commands/effects/refusals/count/state), refusals-in-order, serialization.

Gate: `./gradlew :local-llm:testDebugUnitTest :local-llm:lintDebug --no-daemon`.
