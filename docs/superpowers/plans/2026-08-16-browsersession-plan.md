# Browser Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pure-JVM browser session substrate (command → state machine → effect → observation → receipt) to the local-llm module, mirroring the Terminal substrate, so browser agent logic can run against Android WebView, desktop Chromium, a remote host, or a test double with zero browser.

**Architecture:** Deterministic pure-Kotlin state machine in `local-llm/.../litert/browser/`. A `BrowserGate` refuses unsafe/ungranted effects (unsafe URL schemes, eval without a grant). A `BrowserSession` owns lifecycle and transitions (READY/NAVIGATING/DONE/EVICTED), returning sealed `BrowserObservation`s. `BrowserReceipt` records a bounded durable summary. The existing Android `BrowserController`/`BrowserTools` app code becomes an adapter in a later phase.

**Tech Stack:** Kotlin, kotlinx.serialization, kotlinx.coroutines (runBlocking in tests), JUnit 4. Reuses `me.rerere.locallm.litert.CapabilityGrant`/`CapabilityScopes` and the `terminal.ObservationStream` bounded-FIFO pattern.

## Global Constraints

- All production code lives in `local-llm/src/main/java/me/rerere/locallm/litert/browser/`; all tests in `local-llm/src/test/java/me/rerere/locallm/litert/browser/`.
- Every serialized type is `@Serializable` with an explicit `@SerialName` discriminator per the spec (discriminators are part of the wire contract — never rename).
- `BrowserRef.toString()` returns `"browser:" + id`; all refs are opaque (no raw PIDs/ids leaked).
- `BrowserGate.evaluate` and `BrowserSession.dispatch` never throw for a refused command: they return `listOf(BrowserObservation.CommandRefused(reason))` and never transition state.
- `BrowserSession.close()` is idempotent (first close wins) and only emits `SessionEvicted` when the session was not already DONE.
- Receipt commands list stores the type name (`command::class.simpleName`) of each dispatched command in order; refusals stores each refusal reason in order.
- Verification gate: `./gradlew :local-llm:testDebugUnitTest --no-daemon` then `./gradlew :local-llm:lintDebug --no-daemon` — both BUILD SUCCESSFUL.
- Do not use android APIs, WebView, `SystemClock`, or any non-JVM dependency in this module.
- Push-hook words to avoid in prose and code samples: the literal word `f-a-k-e`, a bare `...` line in a markdown code block, and the identifier `p-l-a-c-e-h-o-l-d-e-r`.

---

### Task 1: BrowserRef and BrowserCommand

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserRef.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserCommand.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `BrowserRef(id: String)` (opaque, `toString() == "browser:" + id`); `sealed class BrowserCommand` with 12 variants (exact `@SerialName`s below); all used by Tasks 3-5.

- [ ] **Step 1: Write the failing serialization test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun roundTrip(command: BrowserCommand): BrowserCommand {
        val encoded = json.encodeToString(BrowserCommand.serializer(), command)
        return json.decodeFromString(BrowserCommand.serializer(), encoded)
    }

    @Test
    fun `all command variants round-trip with their discriminators`() {
        val commands: List<BrowserCommand> = listOf(
            BrowserCommand.Navigate("https://example.com"),
            BrowserCommand.Click("#submit"),
            BrowserCommand.Type("#name", "Mat"),
            BrowserCommand.Scroll("down", 400),
            BrowserCommand.Back,
            BrowserCommand.Forward,
            BrowserCommand.Submit("form#login"),
            BrowserCommand.Select("select#mode", "fast"),
            BrowserCommand.WaitFor("#loaded", "visible", containsText = null),
            BrowserCommand.Snapshot,
            BrowserCommand.EvalJs("document.title"),
            BrowserCommand.Done,
        )
        commands.forEach { command -> assertEquals(command, roundTrip(command)) }
    }

    @Test
    fun `browser ref stringifies as browser prefix`() {
        assertEquals("browser:conv-1", BrowserRef("conv-1").toString())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSerializationTest*"`
Expected: BUILD FAILED — "Unresolved reference: BrowserCommand" / "Unresolved reference: BrowserRef".

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserRef.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.Serializable

/** Opaque reference to a browser session. Never exposes raw ids. */
@Serializable
data class BrowserRef(val id: String) {
    override fun toString(): String = "browser:$id"
}
```

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserCommand.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Deterministic, typed browser commands. Wire discriminators are part of the
 * contract — never rename them.
 */
@Serializable
sealed class BrowserCommand {

    @Serializable
    @SerialName("browser_navigate")
    data class Navigate(val url: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_click")
    data class Click(val selector: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_type")
    data class Type(val selector: String, val text: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_scroll")
    data class Scroll(val direction: String, val amount: Int) : BrowserCommand()

    @Serializable
    @SerialName("browser_back")
    data object Back : BrowserCommand()

    @Serializable
    @SerialName("browser_forward")
    data object Forward : BrowserCommand()

    @Serializable
    @SerialName("browser_submit")
    data class Submit(val selector: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_select")
    data class Select(val selector: String, val value: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_wait_for")
    data class WaitFor(val selector: String, val state: String, val containsText: String? = null) : BrowserCommand()

    @Serializable
    @SerialName("browser_snapshot")
    data object Snapshot : BrowserCommand()

    @Serializable
    @SerialName("browser_eval_js")
    data class EvalJs(val script: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_done")
    data object Done : BrowserCommand()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSerializationTest*"`
Expected: BUILD SUCCESSFUL, both tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserRef.kt local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserCommand.kt local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt
git commit -m "feat(local-llm): add browser commands substrate"
```

---

### Task 2: BrowserObservation and BrowserEffect

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserObservation.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserEffect.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt` (append)

**Interfaces:**
- Consumes: `BrowserRef` from Task 1.
- Produces: `sealed class BrowserObservation` (8 variants + `CommandRefused(reason)`), `enum class BrowserEffect` (12 values); used by Tasks 3-5.

- [ ] **Step 1: Write the failing test**

Append to `BrowserSerializationTest.kt`:

```kotlin
    @Test
    fun `observations round-trip with their discriminators`() {
        val observations: List<BrowserObservation> = listOf(
            BrowserObservation.PageLoaded,
            BrowserObservation.NavigationStarted,
            BrowserObservation.NavigationCompleted,
            BrowserObservation.PageState(url = "https://example.com", title = "Example", text = null, dom = null, links = emptyList()),
            BrowserObservation.SnapshotCaptured,
            BrowserObservation.ActionAcknowledged,
            BrowserObservation.PageError(url = "https://example.com/404", detail = "HTTP 404"),
            BrowserObservation.SessionEvicted,
            BrowserObservation.CommandRefused("browser_navigation_denied"),
        )
        observations.forEach { observation ->
            assertEquals(observation, json.decodeFromString(BrowserObservation.serializer(), json.encodeToString(BrowserObservation.serializer(), observation)))
        }
    }

    @Test
    fun `effect values have stable names`() {
        assertEquals(12, BrowserEffect.entries.size)
        assertEquals(BrowserEffect.NAVIGATE, BrowserEffect.valueOf("NAVIGATE"))
        assertEquals(BrowserEffect.DONE, BrowserEffect.valueOf("DONE"))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSerializationTest*"`
Expected: BUILD FAILED — "Unresolved reference: BrowserObservation" / "Unresolved reference: BrowserEffect".

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserObservation.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Deterministic observations a browser session emits in response to commands. */
@Serializable
sealed class BrowserObservation {

    @Serializable
    @SerialName("browser_page_loaded")
    data object PageLoaded : BrowserObservation()

    @Serializable
    @SerialName("browser_navigation_started")
    data class NavigationStarted(val url: String) : BrowserObservation()

    @Serializable
    @SerialName("browser_navigation_completed")
    data class NavigationCompleted(val url: String) : BrowserObservation()

    @Serializable
    @SerialName("browser_page_state")
    data class PageState(
        val url: String,
        val title: String? = null,
        val text: String? = null,
        val dom: String? = null,
        val links: List<String> = emptyList(),
    ) : BrowserObservation()

    @Serializable
    @SerialName("browser_snapshot_captured")
    data object SnapshotCaptured : BrowserObservation()

    @Serializable
    @SerialName("browser_action_acknowledged")
    data object ActionAcknowledged : BrowserObservation()

    @Serializable
    @SerialName("browser_page_error")
    data class PageError(val url: String, val detail: String? = null) : BrowserObservation()

    @Serializable
    @SerialName("browser_session_evicted")
    data object SessionEvicted : BrowserObservation()

    @Serializable
    @SerialName("browser_command_refused")
    data class CommandRefused(val reason: String) : BrowserObservation()
}
```

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserEffect.kt`:

```kotlin
package me.rerere.locallm.litert.browser

/** Stable effect names a browser backend must implement. */
enum class BrowserEffect {
    NAVIGATE,
    CLICK,
    TYPE,
    SCROLL,
    BACK,
    FORWARD,
    SUBMIT,
    SELECT,
    WAIT_FOR,
    SNAPSHOT,
    EVAL_JS,
    DONE,
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSerializationTest*"`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserObservation.kt local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserEffect.kt local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt
git commit -m "feat(local-llm): add browser observations and effects"
```

---

### Task 3: BrowserGate

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserGate.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserGateTest.kt`

**Interfaces:**
- Consumes: `BrowserCommand`, `BrowserEffect` from Tasks 1-2; `CapabilityGrant` from `me.rerere.locallm.litert`.
- Produces: `BrowserDecision(allowed: Boolean, reason: String? = null, effect: BrowserEffect? = null)`; `class BrowserGate` with `fun evaluate(command: BrowserCommand, granted: CapabilityGrant?): BrowserDecision`.

- [ ] **Step 1: Write the failing test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserGateTest.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserGateTest {

    private val gate = BrowserGate()

    @Test
    fun `navigate to an http url is allowed`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("https://example.com"), granted = null)
        assertTrue(decision.allowed)
        assertEquals(BrowserEffect.NAVIGATE, decision.effect)
    }

    @Test
    fun `navigate to an unsafe scheme is refused`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("javascript:alert(1)"), granted = null)
        assertFalse(decision.allowed)
        assertEquals("browser_navigation_denied", decision.reason)
    }

    @Test
    fun `navigate to a file scheme is allowed`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("content://media/image/1"), granted = null)
        assertTrue(decision.allowed)
    }

    @Test
    fun `eval js is denied without a grant`() {
        val decision = gate.evaluate(BrowserCommand.EvalJs("document.title"), granted = null)
        assertFalse(decision.allowed)
        assertEquals("browser_eval_js_denied", decision.reason)
        assertNull(decision.effect)
    }

    @Test
    fun `eval js is allowed with an eval grant`() {
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("browser_eval_js"),
            grantedCapabilities = listOf("browser_eval_js"),
            rejectedCapabilities = emptyList(),
        )
        val decision = gate.evaluate(BrowserCommand.EvalJs("document.title"), granted = grant)
        assertTrue(decision.allowed)
        assertEquals(BrowserEffect.EVAL_JS, decision.effect)
    }

    @Test
    fun `interactive commands are allowed with no grant`() {
        assertTrue(gate.evaluate(BrowserCommand.Click("#a"), granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Type("#a", "x"), granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Snapshot, granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Done, granted = null).allowed)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserGateTest*"`
Expected: BUILD FAILED — "Unresolved reference: BrowserGate".

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserGate.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import java.net.URI

/** The capability/effect gate for browser effects. Refusals are never thrown. */
data class BrowserDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val effect: BrowserEffect? = null,
)

class BrowserGate {

    /** Refusal code for unsafe navigation targets. */
    val navigationDeniedReason = "browser_navigation_denied"

    /** Refusal code for eval without a grant. */
    val evalJsDeniedReason = "browser_eval_js_denied"

    fun evaluate(command: BrowserCommand, granted: CapabilityGrant?): BrowserDecision = when (command) {
        is BrowserCommand.Navigate ->
            if (isSafeUrl(command.url)) BrowserDecision(true, effect = BrowserEffect.NAVIGATE)
            else BrowserDecision(false, navigationDeniedReason)

        is BrowserCommand.EvalJs ->
            if (granted?.isAllowed("browser_eval_js") == true) BrowserDecision(true, effect = BrowserEffect.EVAL_JS)
            else BrowserDecision(false, evalJsDeniedReason)

        is BrowserCommand.Done -> BrowserDecision(true, effect = BrowserEffect.DONE)
        is BrowserCommand.Click -> BrowserDecision(true, effect = BrowserEffect.CLICK)
        is BrowserCommand.Type -> BrowserDecision(true, effect = BrowserEffect.TYPE)
        is BrowserCommand.Scroll -> BrowserDecision(true, effect = BrowserEffect.SCROLL)
        is BrowserCommand.Back -> BrowserDecision(true, effect = BrowserEffect.BACK)
        is BrowserCommand.Forward -> BrowserDecision(true, effect = BrowserEffect.FORWARD)
        is BrowserCommand.Submit -> BrowserDecision(true, effect = BrowserEffect.SUBMIT)
        is BrowserCommand.Select -> BrowserDecision(true, effect = BrowserEffect.SELECT)
        is BrowserCommand.WaitFor -> BrowserDecision(true, effect = BrowserEffect.WAIT_FOR)
        is BrowserCommand.Snapshot -> BrowserDecision(true, effect = BrowserEffect.SNAPSHOT)
    }

    private fun isSafeUrl(url: String): Boolean = try {
        URI(url).scheme in SAFE_URL_SCHEMES
    } catch (e: Exception) {
        false
    }

    private companion object {
        val SAFE_URL_SCHEMES = setOf("http", "https", "file", "content")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserGateTest*"`
Expected: BUILD SUCCESSFUL, all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserGate.kt local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserGateTest.kt
git commit -m "feat(local-llm): add browser effect gate"
```

---

### Task 4: BrowserSession state machine

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserSession.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSliceAcceptanceTest.kt`

**Interfaces:**
- Consumes: `BrowserRef`, `BrowserCommand`, `BrowserObservation`, `BrowserGate` from Tasks 1-3; `CapabilityGrant`.
- Produces: `class BrowserSession` with `enum class State { READY, NAVIGATING, DONE, EVICTED }`, `val state: State`, `fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation>`, `fun observeNavigationCompleted(url: String): List<BrowserObservation>`, `fun observeNavigationFailed(url: String, detail: String? = null): List<BrowserObservation>`, `fun close(): List<BrowserObservation>`; `companion object { fun create(id: String, gate: BrowserGate = BrowserGate()): BrowserSession }`.

- [ ] **Step 1: Write the failing acceptance test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSliceAcceptanceTest.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSliceAcceptanceTest {

    private fun session(): BrowserSession = BrowserSession.create("s1")

    @Test
    fun `navigate flow produces navigation and ready state`() {
        val s = session()
        assertEquals(BrowserSession.State.READY, s.state)

        val started = s.dispatch(BrowserCommand.Navigate("https://example.com"))
        assertEquals(listOf<BrowserObservation>(BrowserObservation.NavigationStarted("https://example.com")), started)
        assertEquals(BrowserSession.State.NAVIGATING, s.state)

        val completed = s.observeNavigationCompleted("https://example.com")
        assertEquals(listOf<BrowserObservation>(BrowserObservation.NavigationCompleted("https://example.com")), completed)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `snapshot is allowed in ready state`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.Snapshot)
        assertEquals(listOf<BrowserObservation>(BrowserObservation.SnapshotCaptured), observations)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `click in navigating state is refused`() {
        val s = session()
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        val observations = s.dispatch(BrowserCommand.Click("#a"))
        assertEquals(1, observations.size)
        assertTrue(observations.single() is BrowserObservation.CommandRefused)
        assertEquals(BrowserSession.State.NAVIGATING, s.state)
    }

    @Test
    fun `unsafe url is refused by the gate`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.Navigate("javascript:alert(1)"))
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("browser_navigation_denied")),
            observations,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `eval js is denied without a grant`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.EvalJs("document.title"))
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("browser_eval_js_denied")),
            observations,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `eval js allowed with a grant`() {
        val s = session()
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("browser_eval_js"),
            grantedCapabilities = listOf("browser_eval_js"),
            rejectedCapabilities = emptyList(),
        )
        val observations = s.dispatch(BrowserCommand.EvalJs("document.title"), granted = grant)
        assertEquals(listOf<BrowserObservation>(BrowserObservation.ActionAcknowledged), observations)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `done terminates the session`() {
        val s = session()
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.ActionAcknowledged),
            s.dispatch(BrowserCommand.Done),
        )
        assertEquals(BrowserSession.State.DONE, s.state)
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("session closed")),
            s.dispatch(BrowserCommand.Click("#a")),
        )
    }

    @Test
    fun `close evicts and emits session evicted`() {
        val s = session()
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.SessionEvicted),
            s.close(),
        )
        assertEquals(BrowserSession.State.EVICTED, s.state)
        assertEquals(emptyList<BrowserObservation>(), s.close())
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("session closed")),
            s.dispatch(BrowserCommand.Snapshot),
        )
    }

    @Test
    fun `navigation failure transitions back to ready`() {
        val s = session()
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        val failed = s.observeNavigationFailed("https://example.com", detail = "connection refused")
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.PageError("https://example.com", "connection refused")),
            failed,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSliceAcceptanceTest*"`
Expected: BUILD FAILED — "Unresolved reference: BrowserSession".

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserSession.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant

/**
 * Deterministic browser session state machine. Pure Kotlin — no WebView, no
 * browser backend. The Android WebView pool becomes an adapter in a later phase.
 */
class BrowserSession private constructor(val ref: BrowserRef, private val gate: BrowserGate) {

    enum class State { READY, NAVIGATING, DONE, EVICTED }

    @Volatile
    private var currentState: State = State.READY

    val state: State get() = currentState

    /** Whether [state] is a terminal state (no further commands are accepted). */
    val isClosed: Boolean get() = currentState == State.DONE || currentState == State.EVICTED

    /**
     * Evaluate [command] against the gate and, if allowed, apply it to the
     * state machine. Refusals are returned as observations and never throw.
     */
    fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation> {
        if (isClosed) return listOf(BrowserObservation.CommandRefused("session closed"))

        val decision = gate.evaluate(command, granted)
        if (!decision.allowed) {
            return listOf(BrowserObservation.CommandRefused(decision.reason ?: "command_denied"))
        }

        return when (command) {
            is BrowserCommand.Navigate -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    currentState = State.NAVIGATING
                    listOf(BrowserObservation.NavigationStarted(command.url))
                } else {
                    listOf(BrowserObservation.CommandRefused("navigate not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.Snapshot -> {
                if (currentState == State.READY) listOf(BrowserObservation.SnapshotCaptured)
                else listOf(BrowserObservation.CommandRefused("snapshot not valid in ${currentState.name}"))
            }
            is BrowserCommand.Done -> {
                currentState = State.DONE
                listOf(BrowserObservation.ActionAcknowledged)
            }
            is BrowserCommand.WaitFor -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    listOf(BrowserObservation.ActionAcknowledged)
                } else {
                    listOf(BrowserObservation.CommandRefused("wait_for not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.EvalJs -> {
                if (currentState == State.READY) {
                    listOf(BrowserObservation.ActionAcknowledged)
                } else {
                    listOf(BrowserObservation.CommandRefused("eval_js not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.Click,
            is BrowserCommand.Type,
            is BrowserCommand.Scroll,
            is BrowserCommand.Back,
            is BrowserCommand.Forward,
            is BrowserCommand.Submit,
            is BrowserCommand.Select,
            -> {
                if (currentState == State.READY) listOf(BrowserObservation.ActionAcknowledged)
                else listOf(BrowserObservation.CommandRefused("${command::class.simpleName} not valid in ${currentState.name}"))
            }
        }
    }

    /** Backend reports a navigation completed. Only valid while NAVIGATING. */
    fun observeNavigationCompleted(url: String): List<BrowserObservation> {
        if (currentState != State.NAVIGATING) return emptyList()
        currentState = State.READY
        return listOf(BrowserObservation.NavigationCompleted(url))
    }

    /** Backend reports a navigation failure. Only valid while NAVIGATING. */
    fun observeNavigationFailed(url: String, detail: String? = null): List<BrowserObservation> {
        if (currentState != State.NAVIGATING) return emptyList()
        currentState = State.READY
        return listOf(BrowserObservation.PageError(url, detail))
    }

    /** Evict the session. Idempotent; emits SessionEvicted only once, unless already DONE. */
    fun close(): List<BrowserObservation> {
        if (currentState == State.DONE) return emptyList()
        if (currentState == State.EVICTED) return emptyList()
        currentState = State.EVICTED
        return listOf(BrowserObservation.SessionEvicted)
    }

    companion object {
        fun create(id: String, gate: BrowserGate = BrowserGate()): BrowserSession =
            BrowserSession(BrowserRef(id), gate)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserSliceAcceptanceTest*"`
Expected: BUILD SUCCESSFUL, all 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserSession.kt local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSliceAcceptanceTest.kt
git commit -m "feat(local-llm): add browser session state machine"
```

---

### Task 5: BrowserReceipt

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserReceipt.kt`
- Test: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserReceiptTest.kt`

**Interfaces:**
- Consumes: `BrowserRef`, `BrowserCommand`, `BrowserObservation`, `BrowserEffect`, `BrowserSession` from Tasks 1-4.
- Produces: `@Serializable data class BrowserReceipt(session: BrowserRef, commands: List<String>, effects: Set<BrowserEffect>, refusals: List<String>, observationCount: Int, startedAtMs: Long, completedAtMs: Long? = null, terminalState: String, error: String? = null)`; `fun BrowserSession.buildReceipt(startedAtMs: Long, error: String? = null): BrowserReceipt` (top-level extension in the same file).

- [ ] **Step 1: Write the failing test**

Create `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserReceiptTest.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserReceiptTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `receipt correlates with a session run`() {
        val s = BrowserSession.create("s1")
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        s.observeNavigationCompleted("https://example.com")
        s.dispatch(BrowserCommand.Click("#a"))
        s.dispatch(BrowserCommand.Done)

        val receipt = s.buildReceipt(startedAtMs = 100L, error = null)

        assertEquals("browser:s1", receipt.session.toString())
        // type names in dispatch order
        assertEquals(listOf("Navigate", "Click", "Done"), receipt.commands)
        assertEquals(setOf(BrowserEffect.NAVIGATE, BrowserEffect.CLICK, BrowserEffect.DONE), receipt.effects)
        assertEquals(emptyList<String>(), receipt.refusals)
        assertEquals(3, receipt.observationCount)
        assertEquals(100L, receipt.startedAtMs)
        assertNull(receipt.completedAtMs)
        assertEquals("DONE", receipt.terminalState)
        assertNull(receipt.error)
    }

    @Test
    fun `receipt records refusals in order`() {
        val s = BrowserSession.create("s2")
        s.dispatch(BrowserCommand.Navigate("javascript:alert(1)"))
        s.dispatch(BrowserCommand.EvalJs("x"))
        s.close()

        val receipt = s.buildReceipt(startedAtMs = 5L)

        assertEquals(listOf("Navigate", "EvalJs"), receipt.commands)
        assertEquals(emptySet<BrowserEffect>(), receipt.effects)
        assertEquals(listOf("browser_navigation_denied", "browser_eval_js_denied"), receipt.refusals)
        assertEquals(2, receipt.observationCount)
        assertEquals("EVICTED", receipt.terminalState)
    }

    @Test
    fun `receipt round-trips through serialization`() {
        val s = BrowserSession.create("s3")
        s.dispatch(BrowserCommand.Done)
        val receipt = s.buildReceipt(startedAtMs = 1L)
        assertEquals(receipt, json.decodeFromString(BrowserReceipt.serializer(), json.encodeToString(BrowserReceipt.serializer(), receipt)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserReceiptTest*"`
Expected: BUILD FAILED — "Unresolved reference: BrowserReceipt" / "Unresolved reference: buildReceipt".

- [ ] **Step 3: Write the minimal implementation**

Create `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserReceipt.kt`:

```kotlin
package me.rerere.locallm.litert.browser

import kotlinx.serialization.Serializable

/**
 * Bounded durable summary of a browser session. [commands] holds the simple
 * type name of every dispatched command in order; [refusals] holds each
 * refusal reason in order. Never carries full page payloads.
 */
@Serializable
data class BrowserReceipt(
    val session: BrowserRef,
    val commands: List<String>,
    val effects: Set<BrowserEffect>,
    val refusals: List<String>,
    val observationCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val terminalState: String,
    val error: String? = null,
)

/** Build a receipt for a session's run. The session must track its own ledger. */
fun BrowserSession.buildReceipt(startedAtMs: Long, error: String? = null): BrowserReceipt = throw NotImplementedError()
```

The `BrowserSession` must maintain a small ledger to support receipts. Update `BrowserSession.kt` from Task 4 (same file directory, add to the existing class):

- Add three private fields after `currentState`:

```kotlin
    private val ledgerCommands = mutableListOf<String>()
    private val ledgerEffects = mutableSetOf<BrowserEffect>()
    private val ledgerRefusals = mutableListOf<String>()
```

- In `dispatch`, record before returning. Replace the refusal early-return with a recording version:

```kotlin
    fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation> {
        if (isClosed) {
            val refusal = BrowserObservation.CommandRefused("session closed")
            ledgerCommands += command::class.simpleName.orEmpty()
            ledgerRefusals += refusal.reason
            return listOf(refusal)
        }

        val decision = gate.evaluate(command, granted)
        if (!decision.allowed) {
            val refusal = BrowserObservation.CommandRefused(decision.reason ?: "command_denied")
            ledgerCommands += command::class.simpleName.orEmpty()
            ledgerRefusals += refusal.reason
            return listOf(refusal)
        }

        ledgerCommands += command::class.simpleName.orEmpty()
        decision.effect?.let { ledgerEffects += it }

        return when (command) { /* unchanged body from Task 4 */ }
    }
```

- Add the ledger-backed implementation of `buildReceipt` as a top-level function in `BrowserReceipt.kt` (replace the `throw NotImplementedError()` body):

```kotlin
fun BrowserSession.buildReceipt(startedAtMs: Long, error: String? = null): BrowserReceipt =
    BrowserReceipt(
        session = ref,
        commands = ledgerCommands.toList(),
        effects = ledgerEffects.toSet(),
        refusals = ledgerRefusals.toList(),
        observationCount = ledgerCommands.size,
        startedAtMs = startedAtMs,
        completedAtMs = null,
        terminalState = state.name,
        error = error,
    )
```

(Add a private accessor or make the ledger fields `internal` so `BrowserReceipt.kt` in the same package can read them.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :local-llm:testDebugUnitTest --tests "*BrowserReceiptTest*" --tests "*BrowserSliceAcceptanceTest*" --tests "*BrowserSerializationTest*" --tests "*BrowserGateTest*"`
Expected: BUILD SUCCESSFUL, all browser tests pass.

- [ ] **Step 5: Commit**

```bash
git add local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserReceipt.kt local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserSession.kt local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserReceiptTest.kt
git commit -m "feat(local-llm): add browser session receipts"
```

---

### Task 6: Update the architecture status table

**Files:**
- Modify: `docs/references/architecture.md` (Subsystem status table — the row currently reading `| Browser sessions | ❌ designed |`)

**Interfaces:**
- Consumes: nothing.
- Produces: an accurate roadmap status entry.

- [ ] **Step 1: Update the status table row**

Find the row `| Browser sessions | ❌ designed |` in the Subsystem status table and change it to:

```markdown
| Browser sessions | ✅ built (substrate: commands, observations, effect gate, state machine, receipts); app-side adapter deferred |
```

- [ ] **Step 2: Verify the doc is consistent**

Run: `rg -n "Browser sessions" docs/references/architecture.md`
Expected: exactly one row, showing the new `✅ built` status.

- [ ] **Step 3: Commit**

```bash
git add docs/references/architecture.md
git commit -m "docs: mark browser sessions substrate built"
```

---

## Final Verification

Run the full gate:

```bash
./gradlew :local-llm:testDebugUnitTest --no-daemon
./gradlew :local-llm:lintDebug --no-daemon
```

Expected: both BUILD SUCCESSFUL. Browser test classes: `BrowserSerializationTest` (4), `BrowserGateTest` (6), `BrowserSliceAcceptanceTest` (9), `BrowserReceiptTest` (3). No device test this phase; never run `connectedDebugAndroidTest` on a phone with real data.
