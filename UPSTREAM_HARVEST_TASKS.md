# Upstream harvest manual tasks

These changes need a normal patch-capable checkout because the GitHub connector can only replace whole files. Keep each item as a separate commit or pull request.

## 1. Fix streamed tool-name merging

- [ ] Edit `ai/src/main/java/me/rerere/ai/ui/Message.kt`.
- [ ] In `UIMessagePart.Tool.merge`, replace:

```kotlin
toolName = toolName + other.toolName,
```

with:

```kotlin
toolName = if (other.toolName.isBlank()) toolName else other.toolName,
```

Repeated name-bearing stream chunks can currently produce invalid names such as `searchsearch`.

- [ ] Add focused tests in `ai/src/test/java/me/rerere/ai/ui/MessageTest.kt` covering:
  - existing name plus blank incoming name keeps the existing name
  - blank existing name plus incoming name adopts the incoming name
  - existing name plus repeated incoming name does not concatenate the name
  - input fragments still concatenate normally

## 2. Make the per-turn tool-step limit configurable

Preserve the current default of 32 and clamp the setting to 1–500.

- [ ] Add constants and `clampMaxToolSteps()` to `app/src/main/java/me/rerere/rikkahub/data/preferences/TermuxDefaults.kt`.
- [ ] Add the DataStore key, flow, getter/setter, and snapshot field in `TermuxPreferences.kt`.
- [ ] Add `@Volatile var maxToolSteps` to `app/src/main/java/me/rerere/rikkahub/data/ai/limits/ToolRuntimeLimits.kt`.
- [ ] Update the runtime-config collector so the live holder changes when preferences change.
- [ ] In `GenerationHandler.generateText`, change the default from hardcoded `32` to:

```kotlin
maxSteps: Int = ToolRuntimeLimits.maxToolSteps,
```

The default expression must be evaluated per call so a setting change applies on the next turn.

- [ ] Add the Termux settings UI control and ViewModel setter.
- [ ] Add strings for the title, description, and `steps` unit.
- [ ] Add clamping/default tests to `TermuxDefaultsTest.kt`.

## 3. Keep tool arguments out of release logcat

- [ ] In `GenerationHandler.kt`, wrap the per-tool argument log with:

```kotlin
if (BuildConfig.DEBUG) {
    Log.i(...)
}
```

- [ ] Preserve the existing recursive secret redaction for debug builds.

## 4. Verify current merged and open work

Run from a normal checkout:

```bash
./gradlew test assembleDebug
./gradlew connectedDebugAndroidTest
```

- [ ] Confirm merged PRs #3–#6 compile and pass tests.
- [ ] Confirm PR #7, `fix(ai): honor bare base64 for data URL images`, passes.
- [ ] Confirm PR #8, `fix(codex): harden model parsing and stream buffering`, passes.
- [ ] Smoke-test Android activity discovery and direct activity launch.
- [ ] Smoke-test `web_fetch` against a public URL, localhost, a private LAN address, an oversized POST body, and malformed headers.
- [ ] Smoke-test a scheduled job with successes followed by newer failures and verify `max_runs` still stops correctly.
- [ ] Smoke-test Codex streaming for missing characters under a long response.

## Guardrails

- Do not copy ExTV's entire `Message.kt`; its current blob also changes context-window truncation behavior.
- Do not copy ExTV's entire `GenerationHandler.kt`; it has diverged from this fork and would overwrite fork-specific execution and observability work.
- Apply only the narrow edits above, then review the resulting diff before committing.
