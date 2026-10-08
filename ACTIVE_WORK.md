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

## Pixel 10 Pro in-place debug update and FLUX removal fix — 2026-10-07

- **App data safeguard:** created a private, host-side, verified tar archive of the existing debug install's databases, preferences, datastore, skills and small state files before update. Did not copy or change large model weights. Backup path/hash are private machine state and intentionally omitted here.
- **In-place update:** built `:app:assembleDebug` and `:app:assembleDebugAndroidTest` and manually installed both APKs with `adb install -r`. Verified package `excp.rikkahub.local.debug` remained installed, its update timestamp advanced, and pre-existing databases, preferences, and LFM/InternVL/PaddleOCR model bundles remained present. **Never used `connectedDebugAndroidTest`**, because it can uninstall the app.
- **FLUX delete behavior:** observed `SettingsModelRegistry.remove` previously removed a model registration but never deleted on-disk files. FLUX is a directory package rather than a preference-managed model file; this left roughly 6.6 GiB in the app's canonical `local-models/flux2-klein` folder even after removing its listing. Updated that exact FLUX removal path to delete its bounded package tree before deregistering, with no symlink traversal and errors propagated; the UI's existing delete confirmation remains. Focused `Flux2KleinPackageTest` **12/12 PASS**, including preserving neighboring files, rejecting wrong-path deletion, and refusing directory symlinks.
- **Unchanged storage:** **No FLUX model files were deleted during this session.** A separate older `flux-klein` directory (~0.86 GiB) under RikkaHub external storage is not included in the new deletion operation. The separately installed Google FLUX demo also has its own model data (~6.3 GiB) and is not touched. These are distinct removal decisions; do not delete them merely because the active app package is removed.
- **Device CPU inference:** added a manual-only on-device `LiteRtDeviceAcceptanceTest`. Native LiteRT-LM 0.17.0 successfully initialized the existing 736015744-byte LFM2.5 INT4 bundle on **CPU** (first run ~5.2s load), executed generation and released the engine without a crash. The **first weak nonblank-text gate passed but returned only `.`**; it is NOT proof of useful inference. After tightening the semantic gate to require an answer containing `orange`, changing prompt and increasing maxNumTokens to 256, the test **FAILED**: again exactly `.`. Treat generation correctness as unresolved; the model's installed SHA-256 is different from the current upstream manifest's publisher hash, so its exact conversion/revision/SDK compatibility must be investigated before blaming the planner. GPU was not tested.
- Current evidence separates build pass, focused FLUX unit-test pass, native CPU load/release pass, **semantic-output failure**, and model provenance gap. Do not report a successful end-to-end user inference path on the basis of a nonempty output or successful compilation.
- **Remaining:** if user chooses to free FLUX storage, use the repaired Models → FLUX.2-klein → Delete confirmation to remove only the app-owned active FLUX package, then measure reclaimed bytes; discuss old RikkaHub `flux-klein` and standalone Google demo copies separately. For LFM, isolate wrong-output cause with revision-matched publisher artifact / independent source-runtime control and equivalent prompting; preserve installed files until a verified replacement is available.

## Built-in RikkaHub provider removal repair — 2026-10-07

- User confirmed deleting RikkaHub's active FLUX package. ADB verified that `/sdcard/Android/data/excp.rikkahub.local.debug/files/local-models/flux2-klein` no longer exists, while an older separate `flux-klein` directory remains. Approximately 10 GiB was free on device after the deletion; no further FLUX files were removed by this work.
- Investigated the stubborn **RikkaHub** row under Models: built-in OpenAI-compatible provider `RikkaHub` ships a default `Auto` entry. `PreferencesStore` automatically restores missing built-ins unless `deletedBuiltInProviderIds` records user removal. The provider-details Delete implementation did not write this tombstone; additionally, its Delete button was hidden for built-in providers. Manage Sources long-press already tracked deletion correctly, but was undiscoverable.
- Added a single pure `Settings.withoutProvider` implementation and routed both Settings provider-details and Manage Sources removals through it. Built-in provider deletion now records the ID for persistent exclusion; custom provider deletion does not. Provider-details now exposes the Delete confirmation for built-in providers. No model files are deleted by this provider setting removal.
- Validation: `:app:assembleDebug` **PASS** (1m28s); `:app:assembleDebugAndroidTest` **PASS** (9s); `ProviderRemovalDeviceTest` manual `adb shell am instrument` **3/3 PASS**, read-only constructed settings, no live settings altered. Host `:app:testDebugUnitTest` remained blocked before execution by Gradle 9.4.1 Kotlin `destinationDirectory` output-state hashing error (`Invalid file path`). A temporary `doNotTrackState` workaround failed with `Changes are not tracked`; keep this as unverified host JVM coverage, do not call it passing.
- Made a verified private backup of existing app databases/preferences/small state following the FLUX deletion, then `adb install -r` succeeded with the new arm64 debug APK; `excp.rikkahub.local.debug` retained its package and installed LFM2.5 model. Manual test APK installed with `adb install -r`, never ran Gradle `connectedDebugAndroidTest` (unsafe to real app data).
- **User action remains:** RikkaHub provider still exists in live settings as no automatic provider deletion was performed. In the updated app, open Models, tap the gear beside the **RikkaHub** provider group to open Configuration, tap the now-visible trash/delete control and confirm. This removes the built-in provider and its Auto entry; verify no reappearance after restarting. Do not represent in-memory acceptance tests as proof of this full UI + persistence path until confirmed.

## Downloadable LiteRT-Models gallery — 2026-10-07

- **User goal:** Expose the models listed at `https://github.com/john-rocky/LiteRT-Models` as browsable **download options inside RikkaHub**, not merely reuse conversion recipes. Upstream README table (commit `15c41a55bc68caea11b684c10bfe081cff411c08`) currently has **102** named entries. Snapshot stored in `app/src/main/assets/litert_models_gallery.tsv`. Of these, 83 table links point to Hugging Face, 13 to direct GitHub release artifacts, and 6 to scripts/modules. Some 11 HF entries link only to the upstream organization's root (no specific model repository); these, plus script-only entries, display a source link but correctly do **not** invent a download binary.
- **In-app UI:** `Models → + Add → LiteRT-Models gallery` opens searchable model/task list, shows the upstream task/performance/device provenance, and resolves selectable downloadable files when a user opens an entry. HF repo listing resolves against a specific current commit SHA via Hub API; records available LFS SHA-256 and byte size. GH release links resolve directly. No weights are bundled with APK or downloaded automatically.
- **Download:** Reuses existing `ModelInstall.download` streaming, partial/resume, file-magic check and progress; verifies published byte size and LFS SHA-256 when present. Distinct local paths prevent `model.litertlm` filename collisions across publishers. Successful `.litertlm` downloads are registered under local LiteRT-LM chat (actual inference remains unqualified until device generation succeeds); other `.tflite`, `.onnx`, `.bin` etc. assets are saved separately inside app-owned external gallery files and **not** falsely marked as runnable models. Checks available storage against file size + 256 MiB reserve when upstream size is available. Blocks overwrite of existing downloaded file, offers confirmation for removing exactly one non-chat downloaded asset, and keeps chat model removal under existing Models UI.
- **Validation:** `:app:assembleDebug` PASS and `:app:assembleDebugAndroidTest` PASS after the final code changes. Manually executed read-only Android instrumentation on Pixel 10 Pro **4/4 PASS** (source snapshot 102 unique entries, four chat models, no filename collisions, live Hugging Face commit-pinned model metadata with SHA, actual small `cpga_fp16.tflite` ~94KB download with magic and SHA verification). Temporary downloaded test artifact was deleted automatically, confirmed absent. **Not** a runtime inference validation of the non-chat files; UI browsing and manual end-user download of a large model not exercised. The last newly added remove-confirmation UI path was compiled but not tapped in an interactive UI run.
- **Preservation:** Installed updated debug APK with `adb install -r` on Pixel 10 Pro; no uninstall/clear/reset. Existing databases, preferences, and LFM2.5 weight file stayed present. FLUX package previously removed by user remains absent; the separate older FLUX directory and Google demo not touched. Latest storage read ~10,182,360 KiB available. Verified a fresh **core** backup of app databases, preferences, datastore before install; earlier full private backup (2026-10-07) also exists. A more extensive backup attempt hit a corrupt tar stream and was *not* treated as a valid backup. No models downloaded or left on phone from this integration.
- **Follow-up:** The gallery is a dated source snapshot rather than dynamically syncing upstream additions. Support for `CompiledModel` task-specific inference adapters, multi-file package orchestration, per-model license notices/compatibility profiles, gated Hugging Face repos, and complete interactive UI download/delete smoke remain separate work. LFM2.5 returning only `.` remains unresolved and is not changed by this catalog.

## Llamatik 1.10.1 dependency update — 2026-10-07

- Checked `ferranpons/Llamatik` GitHub latest release: v1.10.1, published 2026-08-24; previous RikkaHub `local-llm` pin was 1.10.0. Upstream v1.10.1 fixes repeated embedding requests failing after the first call by clearing stale KV state before decode (PR #171), plus build flag/docs and iOS demo mic permissions. Upstream source-only features must not be assumed to have working integrations simply because release metadata or README mentions them.
- Updated only `local-llm/build.gradle.kts` dependency to `com.llamatik:library:1.10.1` and corrected the nearby stale comment; no native source fork, session rewrites, GPU changes, or MTP enablement. Gradle resolved `com.llamatik:library-android:1.10.1` as its Android runtime dependency.
- Existing llama.cpp JVM regression tests: `:local-llm:testDebugUnitTest --tests 'me.rerere.locallm.llamacpp.*'` **PASS 16/16** across prompt, continuation, and projector locator tests. `:app:assembleDebug` **PASS**. Gradle logged the pre-existing duplicate `libomp.so` packaging warning; it is not evidence of a runtime conflict or fatal build. APK build-tools `zipalign -c -P 16 -v 4` **PASS**. `:app:assembleDebugAndroidTest` **PASS** after a read-only JNI loader test was added (first compilation failed because app androidTest cannot directly import the transitive local-llm implementation dependency; switched to runtime class resolution without adding another artifact).
- **Connected Pixel 10 Pro**: Created and verified a private host-side backup of current databases/shared preferences/datastore. Manually installed both APKs with `adb install -r` (never `connectedDebugAndroidTest`, `pm clear`, or uninstall). Verified same debug package, updated timestamp, databases, preferences, and existing LFM2.5 `.litertlm` still present. Available `/data` after update: 10166504 KiB. No model weights were changed or downloaded.
- Ran the new `LlamatikNativeLibraryDeviceTest` using manual `adb shell am instrument`: **PASS 1/1**, proving packaged Llamatik JNI initializer loads on the device with the shipping Android native binary. **This does not prove GGUF generation, session continuity, embeddings, or image inference**: no GGUF model was discovered under the current debug app's externally stored model files and no destructive model file search/import/reconfiguration was attempted. The upstream 1.10.1 repeated-embedding fix remains only source-backed, not re-measured on this phone.
- **Pending separate work**: qualify real GGUF inference with a known-good pinned model; investigate session identity per model vs per conversation and prompt-template fidelity; validate MTP on an actually supported GGUF; GPU offload requires a genuinely GPU-enabled native build, not merely a nonzero `gpuLayers` parameter. Do not turn a maintenance version bump into an unverified rewrite.

## Verified PP-OCRv5 CompiledModel integration — 2026-10-07

- **Goal:** User authorized useful implementation from john-rocky's litert-samples link; discovered his fork `john-rocky/litert-samples` was 470 commits behind official `google-ai-edge/litert-samples` with no unique commits ahead. Used official upstream commit `83add91158301c911f34c7e0a74c3fa5c2ebc7e1`, Apache-2.0. Selected actual model-zoo PP-OCRv5 as the first functional adaptation instead of pretending all 29 model tasks are immediately executable.
- **Actual inference:** Replaced the old simplified 320px OCR detector/one-box/no-CTC path in `local-llm/.../ocr/PpOcrEngine.kt` with the correct 640px CHW normalized DB detector, connected text regions/unclip, 48x320 cropped/padded recognizer, dictionary of 18k+ characters, and greedy CTC collapse. Ported Google's detector, recognizer, and `CompiledModelRunner` with original Apache-2.0 headers and source provenance into `local-llm/.../ocr/compiled/`. Native buffers and both compiled graphs are closed correctly, GPU attempted first then CPU when GPU execution fails. This is LiteRT `CompiledModel`, not LiteRT-LM.
- **Downloads & activation:** Added `PpOcrVerifiedPackage` containing the model zoo's revision-pinned Hugging Face **three-file package** (two graph files 10,094,464 + 17,173,552 bytes, dictionary 74,012 bytes) and exact SHA-256. Added `Models → Add → LiteRT-Models gallery` featured "Download verified PP-OCRv5 package" and "Use PP-OCRv5 for OCR" actions. Downloader reuses existing resumable `ModelInstall.download`, verifies size+SHA before committing each file, does not overwrite or delete user files, respects free-space reserve. Deletion checks exact files and refuses while configured with TaskOcrLocal. An explicit activation sets/updates TaskOcrLocal provider and OCR model role through the pure `Settings.withPpOcrSelected` helper, preserving unrelated providers and stable IDs; updated SettingsModelRegistry to recognize the local OCR role and report it as READY only if required files exist. Updated provider help. Local OCR extraction now enforces the same timeout guard instead of allowing unlimited computation.
- **Real physical-device acceptance:** Built `:app:assembleDebug` and `:app:assembleDebugAndroidTest` **PASS**. `:local-llm:testDebugUnitTest --tests 'me.rerere.locallm.ocr.*'` **PASS 5/5** (files, dictionary, CTC, multiple text regions/noise). Created and installed the manual-only device test APK (never ran Gradle `connectedDebugAndroidTest`). Downloaded official three model files to a private temporary host staging folder, verified exact file lengths/SHA-256, then placed them in the *previously nonexistent* `/sdcard/Android/data/excp.rikkahub.local.debug/files/local-models/litert-ocr/ppocrv5/` folder. Both `PpOcrVerifiedDeviceTest` tests **PASS 2/2** on Pixel 10 Pro, including a bitmap containing `HELLO 123` processed by real graph execution with returned text **exactly `HELLO 123`**; repeated after final app install, **PASS 2/2**, same text. Two read-only `PpOcrSelectionDeviceTest` constructed-settings tests **PASS 2/2** (preserve providers and models, stable IDs on repeats). Temporary fixture bitmap was deleted. Exact GPU residency was **not** captured from delegate log, so do not label this as verified GPU acceleration; CPU fallback remains possible.
- **App JVM limitation:** Attempting `:app:testDebugUnitTest --tests '...LocalOcrBranchTest'` FAILED **before executing tests** on the known Gradle 9.4.1 Kotlin `destinationDirectory` output hashing issue (`Failed to create MD5 hash for file: Invalid file path`). Don't represent these app JVM tests as passing; the Android read-only acceptance and model-specific host tests provide separate proof. UI manual tap-through of downloader, delete action, and model selector was not performed and should remain unverified until actual interaction.
- **Data preservation:** Verified host-side tar archive of app databases, shared preferences and datastore before in-place debug update. `adb install -r` kept original package, databases/preferences and existing LFM2.5 LiteRT model; no uninstall, settings reset, or other model deletion. The newly staged PP-OCRv5 model files remain on the Pixel for the user to select via **"Use PP-OCRv5 for OCR"**; **do not silently change live OCR default**. Available phone `/data` ~10 GiB after work. No unrelated runtime bump to LiteRT 2.3.0 or LiteRT-LM 0.18.0; separate incompatible artifacts and device qualification needed.
- **Next work:** User can select the verified package in the gallery and exercise a real OCR tool invocation through RikkaHub's model registry; if that end-to-end interactive path fails, inspect the resulting settings/provider registration rather than treating successful engine acceptance as full UI-route verification. Further Model Zoo task adapters (TTS, audio, vision) need individual preprocessing, decoding, and semantic on-device tests.
