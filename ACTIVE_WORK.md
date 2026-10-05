<!-- meristem-template:v1 -->
# Active Work

## Outcome

Harden RikkaHub Local's Android inference selection around a measured execution
profile: device memory + probed accelerator capabilities -> model suitability ->
runtime/backend plan. Preserve the existing capability broker, System-One,
agent-runtime, memory, provider/model, and installed-data boundaries.

## Current state

**Branch:** `aster/device-execution-profile`, branched from
`aster/provider-model-surface-polish@64c1a34e`.

**Reference:** Brownie's device/model selection was inspected as an external comparison.
RikkaHub's existing authority, memory, agent-runtime, scheduled-job, and provider
boundaries were kept. Only the useful execution-profile/model-selection mechanism was
adapted.

## Implemented

- Added `DeviceExecutionProfile`: live total/available RAM, Android low-memory state,
  and measured accelerator capabilities in one snapshot.
- Added pure/JVM-testable `LiteRtExecutionPlanner` for model admission,
  recommendation, and backend choice.
- Refactored `AcceleratorProbe` to expose process-cached capability evidence with an
  explicit refresh path instead of mixing probing and policy.
- Revalidate persisted/session accelerator choices against current capabilities.
- Corrected LiteRT-LM backend truth: SDK 0.17.0 has no NNAPI backend, so LiteRT no longer
  reports NNAPI while silently executing CPU. Generic/task adapters may still use NNAPI.
- Runtime model swaps release the old Engine/Conversation before re-reading live memory,
  avoiding false refusals caused by reclaimable native/KV memory.
- Runtime now refuses unsafe loads before native engine creation when the model cannot
  fit the live free-memory + headroom budget, with a user-readable provider error.
- "Install default" now chooses between curated recommended catalog artifacts from the
  current device profile instead of always hardcoding Qwen. Under low-memory pressure it
  prefers the smaller fitting recommended model.
- Catalog minimum-RAM bands remain advisory ranking evidence; live available RAM is the
  hard admission signal.
- Did not invent adaptive quantization: Q4/Q8 switching remains future work until
  matching verified artifacts exist.
- Documented the execution-profile seam in architecture, local module guidance, and
  Meristem decisions.

## Locked decisions

- Do not replace RikkaHub's agent runtime, memory proposal boundary, capability broker,
  System-One ladder, scheduled-jobs subsystem, or existing provider lifecycle.
- Persisted accelerator choices are evidence of a prior resolved path, not permanent
  truth.
- A runtime fallback is degraded execution and must not be reported as proof the
  preferred backend worked.
- Preserve installed app data and model files.

## Verification

- `git diff --check` — PASS.
- Focused execution-planner / compute / memory tests — PASS.
- `:local-llm:testDebugUnitTest` — PASS: 463 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin` — PASS.
- `assembleDebug` — PASS, including the configured native builds and APK packaging.
- `:local-llm:lintDebug` — PASS.
- ADB reports no connected device, so real-device backend selection/load behavior remains
  unverified in this workspace.

## Follow-up

- On the next connected Android device run, verify the selected backend and model-load
  receipt against the actual runtime outcome.
- Add verified alternate quantized artifacts to the catalog before enabling adaptive
  Q4/Q8 selection.
