# Image Generation Device Acceptance Test

Validates the local Stable Diffusion runtime end to end on real hardware: library load, CPU
backend support, model warm-up, pixel-correct generation, native cancel, and warm-session reuse.

## Data-safety rule

Run this test **only on a disposable install or an emulator**. `connectedDebugAndroidTest`
uninstalls the target package after the instrumentation run, so it must never run against a
phone carrying real user data (primary device: `56290DLCH002PE`). Install development builds
with `adb install -r` only.

## Prerequisites

- A disposable device or emulator with the debug build installed:
  `adb install -r app/build/outputs/apk/debug/app-universal-debug.apk`
- A small Stable Diffusion model file (`*.gguf`, `*.safetensors`, or `*.bin`) reachable from
  the app data directory. The test picks the smallest installed model and skips gracefully
  (`assumeTrue`) when none is present.

## Run

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.data.ai.StableDiffusionDeviceAcceptanceTest
```

## Expected assertions

1. Native library loads (`ensureLoaded`).
2. CPU backend is supported.
3. First `nativeGenerate(512x512)` returns a non-null, non-empty RGBA buffer of exactly
   `512 * 512 * 4` bytes with at least one non-zero byte.
4. `nativeCancel()` is safe to call on an idle session.
5. The session remains warm after generation; a second `nativeGenerate` on the same session
   succeeds with correct dimensions (warm reuse, no model reload).
6. The test always releases the session (`invalidateSession`) in teardown.
