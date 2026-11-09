# Google AI Edge Gallery → RikkaHub: native LiteRT benchmark

Date: 2026-11-08. Scope: one opt-in, on-device LiteRT-LM benchmark, not a Gallery architecture transplant.

## Why

Normal LiteRT chat telemetry in RikkaHub counts characters and divides by four to estimate tokens/second. It is useful as a rough trend, but not a true token measurement, and the old Settings label looked authoritative. Google AI Edge Gallery uses LiteRT-LM's own `benchmark(...)` API, which the pinned `litertlm-android:0.19.0` AAR exposes directly.

## User path

Open **Models → Local · LiteRT → Configuration**, scroll to **Native LiteRT benchmark**, just above the curated catalog. It lists existing registered `.litertlm` files. Select **CPU** or, on a device advertising a GPU delegate, **GPU** (available only when the existing **Try GPU acceleration** preference is ON), then tap **Benchmark** beside the model. No automatic accelerator fallback occurs. A failed native GPU delegate graph compilation is shown as a specific actionable error, not as an opaque SDK stack trace.

The fixed comparable workload runs **3 times**, **256 prefill tokens** and **64 decode tokens**. The UI displays independently timed full-run wall duration for the first run and median of the later two, plus native SDK first-token latency, prefill throughput, and decode throughput. An unavailable SDK metric displays `n/a`, not a fabricated number. We deliberately do **not** display `BenchmarkInfo.initTimeInSecond` as elapsed startup time: on this Pixel it exceeded the entire observed benchmark invocation, so its semantics are not established. Only the currently displayed result is retained; saved runs, charts, and exports are not yet implemented.

This is a deliberate diagnostic action. It closes any warm LiteRT chat engine first and runs the standalone SDK benchmark while holding `LiteRtRuntime`'s mutex. Any later chat request reloads its model. Other Android inference runtimes are not coordinated by this mutex. It allocates a unique temporary benchmark cache under the app's cache directory, then removes only that cache. It neither downloads models nor changes user preferences. CPU is the default. GPU failures are reported rather than silently rerouting to CPU.

Ordinary chat performance samples remain character-derived estimates; their Settings/Doctor descriptions now say so explicitly. No always-on profiling was added to the chat path.

## Files

- `local-llm/.../litert/LiteRtRuntime.kt`: standalone benchmark call, exclusive mutex, release, temporary cache, progressive completion.
- `local-llm/.../litert/LiteRtBenchmarkSummary.kt`: monotonic wall time, native throughput validation, first-vs-later run medians, missing-value propagation.
- `local-llm/.../litert/LiteRtBenchmarkAvailability.kt` and its JVM test: explicit opt-in/capability policy, and recognition of native GPU delegate graph compilation failure without mislabelling unrelated/CPU errors.
- `app/.../setting/components/LiteRtBenchmarkPanel.kt`: opt-in UI for installed models.
- `app/.../setting/components/ProviderConfigure.kt`: panel entry point.
- `app/src/main/res/values*/lite_rt_benchmark.xml`: localized labels.
- `local-llm/.../litert/LiteRtBenchmarkSummaryTest.kt`: RED-first pure math/missing-count tests.
- `app/src/androidTest/.../data/ai/LiteRtNativeBenchmarkDeviceTest.kt`: manual-only CPU one-run, CPU full-preset three-run, and explicit opt-in GPU native smoke tests.

## Verification (2026-11-08)

- RED: new `LiteRtBenchmarkSummaryTest` initially failed compilation because the implementation was missing. The wall-duration correction also had a RED-first test before replacing the untrustworthy SDK initialization field.
- GREEN: focused test passed after both implementations.
- `:app:assembleDebug` passed.
- Full `:local-llm:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` passed offline with `--max-workers=2`. An earlier all-at-once run had one unrelated, non-reproducible micro-agent cancellation test failure; that test passed alone and the complete suite passed on the final run.
- Physical Pixel 10 Pro, debug package only: used the existing app-private `LFM2.5-1.2B-Instruct_int4.litertlm` (736,015,744 bytes), CPU, **one run** with 64 prefill / 16 decode tokens. The native API succeeded on three instrumented runs, all **OK (1 test)**. The second run exposed the SDK inconsistency: it reported **17,572 ms initialization** for a whole operation measured independently at **10,791 ms**. After correcting the app code to time the full run using monotonic time, the third test reported **21,774.9 ms** run duration versus **21,925 ms** measured around the call; native prefill **13.2569 tok/s**, decode **25.0759 tok/s**, and first-token latency **4867.6 ms**. These are single-run smoke measurements, not the full three-run UI workload or a controlled performance comparison.
- **Full preset native CPU instrumentation:** `threeRunCpuPresetReportsProgressAndMeasuredMedians`, 3 × 256/64, **OK (1 test)**. Progress callbacks `[1,2,3]`; enclosing wall 35,594 ms; first run 14,989.1 ms; later-run median 10,237.9 ms; first-token median 2,469.6 ms; prefill median 105.29 tok/s; decode median 26.17 tok/s.
- **Actual Compose UI path:** manually navigated from the debug app drawer to Settings → Models → Local · LiteRT → Configuration on the physical Pixel. Confirmed installed model rows, CPU/GPU segments, disabled controls and 0/3 progress after tapping the LFM2.5 button, and visibly populated results on completion. Three-run UI result: first run 13,231.6 ms, later-run median 8,050.7 ms; first-token median 1,951.4 ms, prefill median 133.9 tok/s, decode median 25.7 tok/s. Runtime conditions differed from standalone instrumentation; do not interpret the difference as a controlled regression or improvement.
- **GPU smoke failure (observed):** Pixel 10 Pro / Tensor G5 with *Try GPU acceleration* currently **ON** (visually verified live switch), LFM2.5 model, LiteRT-LM 0.19.0, 1 × 64/16 GPU: instrumentation **FAILED** inside `nativeCreateBenchmark` with `LiteRtLmJniException: Failed to modify graph with delegate`. No native crash was observed. This is specific to that model, device, and SDK; it does **not** prove that other GPU model bundles cannot run. This manual hardware test intentionally records a failing GPU acceptance case, while the user-facing benchmark catches and explains the exception. **The exact failure was also reproduced with the on-screen Benchmark button after the guard and error guidance were installed:** it displayed "The GPU delegate could not compile this model on this device. Try CPU; no fallback was used." The debug activity remained foreground and controls re-enabled. Never silently switch backend to pretend GPU succeeded.
- APK and test APK were installed by `adb install -r` without data clear/uninstall. A SHA-verified backup of the debug app's DB, preferences, and DataStore exists outside Git. No release app installation or user media access.

## Not yet verified

- Accessibility, orientation/process-lifecycle recovery, and GPU execution on other models/devices remain unverified. The actual new GPU-failure message was confirmed on the physical phone. The native GPU test failed on the current Tensor G5/LFM2.5 combination. The NPU benchmark is intentionally not exposed.
- Cross-model comparisons under identical thermal/power conditions; the 256/64 three-run CPU preset and its UI end-to-end path are physically verified.
- Repeatability under controlled temperature/charging state, performance/energy implications, user-visible model reload timing after benchmark, saved results, and export/reporting.
- Benchmarks across other backends, models, and devices. Failed native runs must not be interpreted as generic device incompatibility.

Source reference: https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/benchmark/BenchmarkViewModel.kt
