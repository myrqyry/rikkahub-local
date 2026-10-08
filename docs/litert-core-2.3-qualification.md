# LiteRT core 2.3.0 isolated compatibility probe (2026-10-08)

**Status: build-level probe only; NOT approved for installation on a personal handset or release.**

## Scope

This branch is based on the last verified RikkaHub local commit `bfe7dbd3` and changes the three Android module pins (app, speech, local-llm) from `com.google.ai.edge.litert:litert:2.1.5` to 2.3.0, adding the newly split `litert-api:2.3.0` and `litert-gpu:2.3.0` artifacts. LiteRT-LM remains at 0.17.0 here. The independent LiteRT-LM 0.18.0 API port lives on `aster/device-execution-profile`, commit `0d6805b2`. **Do not combine the branches without fresh build and real device tests.**

Google LiteRT 2.3.0 was published October 7, 2026; the 2.4.0 version-file commit on upstream main is not a published release. The 2.3 Android AAR manifests use distinct namespaces (`com.google.ai.edge.litert.impl` for `litert`, `com.google.ai.edge.litert` for `litert-api`, `com.google.ai.edge.litert.gpu` for the GPU artifact). The new minSdk is 24, below this app's minSdk 26.

## Qualification evidence

The isolated worktree build completed with `BUILD SUCCESSFUL in 3m 26s`: `./gradlew :app:assembleDebug :local-llm:testDebugUnitTest --tests 'me.rerere.locallm.ocr.*' --console=plain`. The focused PP-OCR JUnit XML reported **5 tests, 0 failures, 0 errors**. Log: `/tmp/rikkahub-litert-core-23-build4-20261008.log`. Earlier probe attempts failed due to missing local.properties and uninitialized submodules, then the absent ignored LiteRT C++ SDK; these were fresh-worktree environment prerequisites, not LiteRT build failures.

For this experiment only, native submodule directories at the *exact same checked-out commits* and the existing locally bootstrapped `app/src/main/jni/litert/litert_cc_sdk` were temporarily symlinked from the main repo. Those test-only links were removed after the build; the original directories were not modified. The existing C++ SDK is still from **LiteRT 2.1.5**, whereas Maven `litert:2.3.0` supplies the library packaged in the APK. This combination requires verification of native ABI behavior and consistent headers before promotion.

APK evidence:
- `lib/arm64-v8a/libLiteRt.so` SHA-256 `e5b7e3413a8b428b1a1b669f368cf613058868bc7b63e10d0b9c2e6ba55c97d8` matches the Maven 2.3.0 AAR, not the old local C++ library.
- `lib/arm64-v8a/liblitert_jni.so` and `lib/arm64-v8a/libLiteRtClGlAccelerator.so` are present.
- Dynamic-ELF symbol-name audit: the local `liblitert_native_bridge.so` imports 12 LiteRT API symbols; **all 12** are exported by the 2.3.0 `libLiteRt.so`. That is *not* ABI semantic, struct-layout, lifetime, or runtime compatibility proof.
- Existing nonfatal `libomp.so` packaging warning persists.

## Before any promotion

1. Regenerate the C++ LiteRT SDK at the *matching* 2.3.0 headers/prebuilt revision and validate that JNI bridge against it; do not suppress native-link warnings or silently mix old/new ABI contracts.
2. Run Android device tests for PP-OCR detector+recognizer and core CompiledModel; confirm output semantics and resource cleanup rather than only loading JNI.
3. Verify GPU accelerator fallback and real hardware acceleration on the Pixel, not inferred from bundled libraries.
4. Independently test the 0.18.0 LiteRT-LM embedding and LFM chat paths in the same build; this standalone branch intentionally did not mix those upgrades.
5. Protect app-private data and use `adb install -r` only after a verified backup. Never run `connectedDebugAndroidTest` on the live installation.

This document reports a limited successful build probe, not an upgrade recommendation.
