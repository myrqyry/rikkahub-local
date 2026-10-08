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
  stay within the live free-memory + headroom budget, with a user-readable provider error.
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

## Connected Pixel 10 Pro probe — 2026-10-07 (read-only, not native inference qualification)

- ADB on `yuzu-mint` now sees a physical **Pixel 10 Pro**, Tensor G5, arm64-v8a, Android **17** (API **37**). This supersedes the no-device observation in the prior Verification section without rewriting that historical result.
- The installed `excp.rikkahub.local.debug` application is version 2.4.1 / code 171, last updated 2026-09-29. The current working branch is newer; the installed APK cannot validate the new `DeviceExecutionProfile` / `LiteRtExecutionPlanner` integration.
- The debug app still holds existing LiteRT bundles including LFM2.5-1.2B-Instruct INT4, InternVL3-1B, and PaddleOCR-VL-1.6. None were changed, removed, or redownloaded.
- **Device artifact observation:** installed `LFM2.5-1.2B-Instruct_int4.litertlm` is exactly 736015744 bytes and hashes to SHA-256 `a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906`. The published manifest fetched 2026-10-07 from HF repo revision `a986969f90e60e694169b22e38b60a86e65c0448` describes a CPU-only variant of the *same filename and size*, but lists SHA-256 `96041e6c72b1d2e9b73a122870606bbf18a3819bf14c43c3f2b74685ed62c24d`. This is **not the same verified artifact**; it may be an older conversion, but corruption was not ruled out. Do not project the current manifest's backend claims onto the installed file without identifying its source revision.
- Device `/data` had about 1,044,696 KiB free, battery 39% at inspection. Existing user app storage is substantial. **No `adb install -r`, cache pruning, data migration, model load, or inference execution** was attempted: with the near-full filesystem and older installed APK, that would introduce unnecessary data/migration and native-cache risks.
- Host validation on current source: `./gradlew :local-llm:testDebugUnitTest --tests 'me.rerere.locallm.litert.LiteRtExecutionPlannerTest' --console=plain` — **PASS** (exit 0, BUILD SUCCESSFUL in 40s). This validates pure planner tests, not actual Android backend selection or generated output.

**Next safe qualification gate:** preserve/verify installed-data backup according to the project's upgrade rules; obtain enough free device storage for an in-place debug update and native/cache headroom without deleting existing user data automatically; reconcile model file provenance against an immutable HF revision/checksum; then exercise a controlled LiteRT-LM load, generation and release with explicit backend/fallback, memory, and first-token/output receipts. Keep native Android and Lil Tart browser evidence separate.

## Connected-device storage recovery — 2026-10-07

- User authorized clearing only the rebuildable InternVL3-1B and PaddleOCR-VL-1.6 LiteRT acceleration caches. Before deletion, verified the debug app process was not running and listed each exact file.
- Removed five explicitly named generated caches (InternVL XNNPACK; PaddleOCR vision-encoder mldrift program/weight, vision-adapter XNNPACK, and main XNNPACK); did **not** clear app data, caches for LFM2.5, model bundles, configuration, databases, TTS, llama.cpp, or FLUX files.
- Read-only post-check confirmed all three original `.litertlm` bundles are still present and LFM2.5 acceleration caches remain. `adb shell df -k /data` showed free space increase from **475968 KiB** immediately before cleanup to **3090488 KiB** afterward (~2.49 GiB net observed; concurrent Android storage activity may affect this delta). Five removed files themselves were roughly 2.07 GiB.
- App remains the September 29 debug install. No replacement APK was installed and no model generation was attempted. Future safe device testing should first ensure adequate space and data backup, then perform an in-place update and capture real backend/output evidence.
