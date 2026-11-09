<!-- meristem-template:v1 -->
# Errors

Preserve costly, recurring, or non-obvious failures that future agents could plausibly repeat.

Use entries like:

## YYYY-MM-DD — Error / regression

**Observed failure:**  
**Root mechanism:**  
**Affected environment:**  
**Recovery / fix:**  
**How to detect earlier:**  
**Evidence / references:**

## 2026-11-08 — Native LiteRT-LM GPU benchmark delegate compilation fails on Tensor G5 + LFM2.5

**Observed failure:** Explicit GPU 64-prefill/16-decode native benchmark of the installed `LFM2.5-1.2B-Instruct_int4.litertlm` returned `LiteRtLmJniException` from `nativeCreateBenchmark`: `Failed to modify graph with delegate`. Instrumentation FAILED; the debug process stayed alive. The same error was reproduced by the real Benchmark button, which now shows actionable guidance.

**Root mechanism:** GPU LiteRT delegate graph modification fails for the exact tested model/runtime/device combination; deeper incompatibility cause and alternate model support are **not established**. No CPU fallback occurred during GPU benchmarking.

**Affected environment:** Physical Pixel 10 Pro / Google Tensor G5, installed 736,015,744-byte LFM2.5 bundle of unknown exact upstream revision, LiteRT-LM Android 0.19.0. The device's existing *Try GPU acceleration* preference was ON; delegate capability detection returned supported. Advertised support is not proof that an individual model's GPU graph compiles.

**Recovery / fix:** Use CPU for benchmarking that model on that phone. RikkaHub's benchmark panel honors the user's GPU opt-in and delegate capability, catches this specific failure to show a comprehensible message, and never silently reroutes requested GPU runs to CPU. Do **not** disable GPU globally or conclude other models cannot use it.

**How to detect earlier:** Run the manual `LiteRtNativeBenchmarkDeviceTest.gpuShortRunOnlyWhenUserHasOptedIn` separately on a device with opt-in and an installed fixture; it currently reports failure. Test the actual Compose error surface; preserve model files and app data.

**Evidence / references:** `docs/integrations/gallery-litert-native-benchmark-2026-11-08.md`, `LiteRtBenchmarkAvailabilityTest.kt`, full native stack in debug instrumentation output `/tmp/rikka-benchmark-gpu-short.log` on yuzu-mint (transient local log).

## 2026-10-13 — ThemeFamily seed packing collapses accent selection

**Observed failure:** Initial `MeristemThemeTest` failed: the new palette's `blue` and `teal` selections produced exactly the same resolved primary color despite different declared source ARGBs. The test passed after the new family began supplying raw ARGB longs.

**Root mechanism:** The pre-existing `ThemeFamily.seed(hex)` constructs `Color(hex)` and takes `.value.toLong()` (Compose packed color representation). `CustomTheme.generateColorScheme` instead interprets `primaryColorArgb.toInt()` as a 32-bit ARGB source. These are different encodings.

**Affected environment:** The new Meristem family initially, and *potentially* other existing theme families that use `seed()`. Their resulting color behavior was not individually tested and must not be silently changed here.

**Recovery / fix:** The new `meristem` family uses direct `0xFFRRGGBB` Long values and validates different accent outputs. Existing theme definitions and the `seed()` helper remain unchanged until a separate scoped palette audit considers the appearance changes and persistence implications.

**How to detect earlier:** Assert that two named accents produce different resolved `ColorScheme.primary` values, in addition to checking light/dark contrast and preset registration.

**Evidence / references:** `ui/theme/{ThemeFamily.kt,CustomTheme.kt}`, `ui/theme/MeristemThemeTest.kt`; first focused JUnit run failed at `MeristemThemeTest.kt:30`, corrected rerun passed 2/2.
