# Phase J — Browser Session Substrate (Pure-JVM Seam) Design

> **Status:** Approved by user (m2011). Execution: inline TDD per plan.
> **Roadmap:** architecture.md line 157 'Browser sessions ❌ designed' — stale like Terminal was; the app-side WebView browser already ships. Phase J extracts the *portable contract*.

## Background

The app already contains a complete headless browser: `app/.../browser/HeadlessBrowserSession` (offscreen 1080x1920 WebView, Page-Visibility shim, shared `browser-profile` cookies, per-conv pool, idle eviction), `BrowserController` (657 lines), and a 16+ tool surface in `BrowserTools.kt` (`browser_open`, `browser_current_url`, `browser_screenshot`, `browser_get_text`, `browser_get_dom`, `browser_get_links`, `browser_back`, `browser_forward`, `browser_wait_for`, `browser_click`, `browser_type`, `browser_scroll`, `browser_submit`, `browser_select`, `browser_press_key`, `browser_eval_js`, `browser_click_and_read`, `browser_done`).

What is missing is the **portable browser contract**: a deterministic, JVM-testable substrate in local-llm mirroring the Phase F Terminal seam (ProcessRef/TerminalChunk/ProcessGate/TerminalSession/ProcessReceipt + ObservationStream). This lets the same browser-oriented agent logic run against Android WebView, desktop Chromium, a remote host, or a test double with zero browser. Terminal and Browser become the same reusable pattern:

```
command → state machine → effect → observation → receipt
```

## Scope

**IN:** Pure-JVM `browser` substrate in `local-llm/.../litert/browser/` — BrowserRef, BrowserCommand, BrowserObservation, BrowserEffect + BrowserGate, BrowserSession (deterministic state machine), BrowserReceipt; JVM acceptance tests; doc update.

**OUT:** Semantic-snapshot normalization (DOM/text/a11y projection) — deferred follow-up. App-side WebView adapter + tool rewiring — deferred follow-up (mirrors how Terminal deferred AgentRun wiring). web/desktop hosts. No WebView ever in local-llm.

## Types

All `@Serializable`, package `me.rerere.locallm.litert.browser`.

### BrowserRef
```kotlin
@Serializable data class BrowserRef(val id: String)
```
Opaque session identifier; no WebView/process handle. `toString()` renders `browser:<id>` (mirror ProcessRef's `process:<id>`).

### BrowserCommand (sealed)
- `Navigate(url: String)` @SerialName("browser_navigate")
- `Click(selector: String)` @SerialName("browser_click")
- `Type(selector: String, text: String)` @SerialName("browser_type")
- `Scroll(direction: String, amount: Int)` @SerialName("browser_scroll") — direction "up"|"down"|"left"|"right"
- `Back()` @SerialName("browser_back")
- `Forward()` @SerialName("browser_forward")
- `Submit(selector: String)` @SerialName("browser_submit")
- `Select(selector: String, value: String)` @SerialName("browser_select")
- `WaitFor(selector: String, state: String, containsText: String? = null)` @SerialName("browser_wait_for")
- `Snapshot()` @SerialName("browser_snapshot")
- `EvalJs(script: String)` @SerialName("browser_eval_js")
- `Done()` @SerialName("browser_done")

### BrowserObservation (sealed)
- `PageLoaded(url: String, title: String?)` @SerialName("browser_page_loaded")
- `NavigationStarted(url: String)` @SerialName("browser_navigation_started")
- `NavigationCompleted(url: String)` @SerialName("browser_navigation_completed")
- `PageState(url: String, title: String?, text: String?, dom: String?, links: List<String>)` @SerialName("browser_page_state") — semantic snapshot payload lives here (deferred normalization; raw pass-through this phase)
- `SnapshotCaptured(description: String)` @SerialName("browser_snapshot_captured")
- `ActionAcknowledged(action: BrowserCommand)` @SerialName("browser_action_acknowledged")
- `PageError(url: String, detail: String)` @SerialName("browser_page_error")
- `SessionEvicted(reason: String)` @SerialName("browser_session_evicted")

### BrowserEffect (enum)
`NAVIGATE, CLICK, TYPE, SCROLL, BACK, FORWARD, SUBMIT, SELECT, WAIT_FOR, SNAPSHOT, EVAL_JS, DONE`

### BrowserGate
Mirror ProcessGate (capability/effect gate). `data class BrowserDecision(val allowed: Boolean, val reason: String? = null, val effect: BrowserEffect? = null)`.
```kotlin
class BrowserGate(private val evalJsCapability: String = "browser_eval_js") {
    fun evaluate(command: BrowserCommand, granted: CapabilityGrant? = null): BrowserDecision
    fun effectOf(command: BrowserCommand): BrowserEffect
    fun isSafeUrl(url: String): Boolean
}
```
Rules:
- `Navigate` → effect NAVIGATE; allowed only if `isSafeUrl(url)` (scheme ∈ {http, https, file, content} via `java.net.URI`, catch → false). Refusal: `"browser_navigation_denied"` (unsafe URL scheme).
- `EvalJs` → effect EVAL_JS; allowed only when the grant includes the eval capability; otherwise `"browser_eval_js_denied"`. **Denied by default** (no grant).
- `Done` → effect DONE; always allowed.
- All other commands → their effect; allowed when no grant provided (interactive) — gate is the *effect* classifier + safety refusal; per-capability refusal mirrors `process_execute`/`process_network` pattern and is JVM-testable with a synthetic grant.
- `granted: CapabilityGrant` (me.rerere.locallm.litert.CapabilityGrant) has `grantedCapabilities: List<String>`, `rejectedCapabilities: List<String>`, `requestedCapabilities: List<String>`.

### BrowserSession
Mirror TerminalSession. Single lifecycle owner, deterministic state machine.
```kotlin
class BrowserSession private constructor(
    val ref: BrowserRef,
    val observations: ObservationStream,
    val gate: BrowserGate,
) {
    val state: State
    enum class State { READY, NAVIGATING, DONE, EVICTED }
    companion object { fun create(id: String, gate: BrowserGate = BrowserGate()): BrowserSession }
    fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation>
    fun close()
}
```
State machine (from user's design, verbatim semantics):
- `Navigate(url)` valid in READY or NAVIGATING → returns `[NavigationStarted(url)]` + transitions to NAVIGATING; the backend later completes it via `observeNavigationCompleted(url)` (a backend-only completion path) → READY.
- `Click/Type/Scroll/Submit/Select/Back/Forward/WaitFor/Snapshot` valid in READY (and Snapshot also in NAVIGATING? No — Snapshot in READY only; WAIT_FOR in NAVIGATING is the legitimate wait path). Rules:
  - `Snapshot` valid in READY → `[SnapshotCaptured(...)]` stays READY.
  - `WaitFor` valid in READY or NAVIGATING → `[ActionAcknowledged]` stays.
  - interactive actions (Click/Type/Scroll/Submit/Select/Back/Forward) valid in READY → `[ActionAcknowledged]` stays READY (a navigation may follow; the app-side backend decides). If state is DONE/EVICTED → refusal `BrowserSessionRefused`... but observations are sealed; use `ActionAcknowledged`? No — refusal must be observable. Add a refusal observation? Keep sealed set minimal: refusal paths emit `PageError(url="", detail=reason)`? Too coarse. **Decision:** gate refusal happens BEFORE dispatch (dispatch returns empty list + caller reads decision); state-refusal returns a single `SessionEvicted`-style? No. Cleanest: `dispatch` returns `List<BrowserObservation>`; on state mismatch it returns `listOf(SessionEvicted("command X not valid in state Y"))`? That conflates. **Final:** introduce one more observation `CommandRefused(reason: String)` @SerialName("browser_command_refused") — clean, explicit, testable. The gate refusal path (unsafe URL / eval denied) also returns `[CommandRefused(reason)]` and never transitions state.
- `EvalJs` valid in READY, subject to gate (denied by default) → `[CommandRefused("browser_eval_js_denied")]` unless granted.
- `Done` valid in any non-terminal state → `[ActionAcknowledged(Done), SessionEvicted("browser_done")]`? The app sends browser_done to tear down. `Done` → state DONE + returns `[ActionAcknowledged(Done)]`; subsequent dispatch returns `[CommandRefused("session closed")]`.
- `close()` → idempotent; if state READY/NAVIGATING → state EVICTED + emit `SessionEvicted(reason)` to observations (if not already DONE).

Backend completion path: `fun observeNavigationCompleted(url: String)` and `fun observeNavigationFailed(url: String, detail: String)` — only valid in NAVIGATING → READY, emitting `NavigationCompleted(url)` or `PageError(url, detail)`. In any other state these are no-ops (return false).

`dispatch` and completion methods append to `observations` (ObservationStream, bounded FIFO, from terminal package — reuse `me.rerere.locallm.litert.terminal.ObservationStream`).

### BrowserReceipt
```kotlin
@Serializable data class BrowserReceipt(
    val session: BrowserRef,
    val commands: List<String>,      // command type names in order
    val effects: Set<BrowserEffect>, // effects that reached decision-allowed
    val refusals: List<String>,      // reasons in order
    val observationCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val terminalState: String,       // BrowserSession.State.name
    val error: String? = null,
)
```
Bounded durable summary; correlated to the session by `ref`. (No raw page content ever lives on the receipt.)

## Testing (JVM, mirrors TerminalSliceAcceptanceTest)

`local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSliceAcceptanceTest.kt` — junit runBlocking + deterministic fakes (no real browser):

1. `navigateFlowProducesNavigationAndReadyState` — dispatch Navigate → NavigationStarted + state NAVIGATING; observeNavigationCompleted → NavigationCompleted + READY.
2. `snapshotIsAllowedInReadyState` — after load, Snapshot → SnapshotCaptured, stays READY.
3. `clickInNavigatingStateIsRefused` — while NAVIGATING, Click → CommandRefused("...not valid...").
4. `unsafeUrlIsRefusedByGate` — Navigate("javascript:alert(1)") → CommandRefused("browser_navigation_denied"), no NavigationStarted.
5. `evalJsIsDeniedWithoutGrant` — EvalJs → CommandRefused("browser_eval_js_denied").
6. `evalJsAllowedWithGrant` — grant with eval capability → ActionAcknowledged, stays READY.
7. `doneTerminatesSession` — Done → ActionAcknowledged; next command → CommandRefused("session closed").
8. `closeEvictsAndEmitsObservation` — close() on READY → state EVICTED + SessionEvicted in observations.
9. `receiptCorrelatesWithSession` — after several commands, BrowserReceipt built from session state has matching ref, command count, effects, refusal reasons.

Plus serialization round-trip test for all new types (commands, observations, receipt) — or fold into the acceptance file as a test class. Prefer a separate `BrowserSerializationTest.kt`.

Gate: `./gradlew :local-llm:testDebugUnitTest --no-daemon` + `./gradlew :local-llm:lintDebug --no-daemon`.

## Files

- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserRef.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserCommand.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserObservation.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserGate.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserSession.kt`
- Create: `local-llm/src/main/java/me/rerere/locallm/litert/browser/BrowserReceipt.kt`
- Create: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSliceAcceptanceTest.kt`
- Create: `local-llm/src/test/java/me/rerere/locallm/litert/browser/BrowserSerializationTest.kt`
- Update: `docs/references/architecture.md` status table — Browser sessions → ✅ built (substrate); note app-side adapter deferred.

## Out of scope (future)

Semantic snapshot normalization (DOM/a11y projection), app-side BrowserBackend adapter + 16 tool rewiring, remote/desktop browser hosts, Compute abstraction, append-only traces.
