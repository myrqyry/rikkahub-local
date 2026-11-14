# AndroidWorld-inspired click postconditions

## Objective and scope

The small November 13, 2026 integration takes the **observe → act →
observe → evaluate** idea from
[AndroidWorld](https://github.com/google-research/android_world) without
importing its Python emulator runner or changing RikkaHub's Android
accessibility permission/approval model.

`click_node`, implemented by
`app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/FindNodeTool.kt`,
remains an existing explicit UI tool. By default it behaves as before:
`success` means `AccessibilityNodeInfo.ACTION_CLICK` returned true.
That is **action acceptance, not evidence of completed user intent**.

Optional postcondition inputs:
- `expect_text`: exact visible accessibility text to appear.
- `expect_absent_text`: exact text to disappear.
- `expect_package`: expected foreground package name.
- `verify_timeout_ms`: bounded observation timeout, default 1,800 ms,
  maximum 5,000 ms; only relevant when a postcondition is provided.

When requested, a fresh accessibility window observation is checked
after the click. The result supplies `verification.status` with
`passed`, `already_satisfied`, `not_observed`,
`action_rejected` or `not_requested`, and a separate
`postcondition_verified` boolean. A condition already satisfied before
a click is **not** credited as newly achieved. Only the requested text
matches and foreground package are extracted for the check; no
automatic screenshots or unrelated UI text are logged.

This verifies only the **specified observable postcondition**. A UI
condition might appear without the broader task being complete.
It does not add a generic planning loop, screenshot-based VLM agent,
observation identity/stale-node guard, or AndroidWorld benchmark harness.

## Verification

Initially implemented and locally committed as
`d84fe7231` on `aster/android-world-verified-click`.
RED-first pure evaluator tests and existing click-tool tests passed
together: **13 JVM tests, zero failures**, and
`:app:compileDebugKotlin` succeeded.

No device/emulator execution of this specific click path has been
performed. Full `./gradlew test` was blocked by an unrelated
`local-llm/GgufClassifierTest` sparse-file test with
`No space left on device`; a full APK from the isolated worktree
was blocked by missing native CMake submodule source folders.
After merge into the main RikkaHub working branch, the combined app and speech
unit tasks (1,836 tests; no failures) and `:app:assembleDebug` (293 tasks)
passed. These results establish integrated build compatibility, but **not**
end-to-end Android UI verification.

The next acceptance step is a disposable-emulator scenario covering
a successful UI transition, an already-satisfied condition, an
unobserved/timeout condition and a rejected action, with real
authorization and receipt flow still enforced. This remains open
in `ACTIVE_WORK.md`.
