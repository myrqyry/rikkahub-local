# Vendored Scylla's Band v2 — attribution and build contract

Source: https://github.com/lowkeytea/scyllasband
Pinned source commit: 2aeafb99a8bad018db29af6024e575d65c651731 (2026-10-09).
License: Apache-2.0, with original LICENSE included.
Vendored portions: C/C++ runtime, LiteRT 2.2.0 C headers, Android JNI bridge
and Kotlin API. The Kotlin adapter is modified to take a separately downloaded
model directory, not embedded model assets.

The model weights are neither vendored in git nor baked into the APK.
The provider catalog pins spybyscript/scyllasband at
0c44c02f1cad9c108ba44c520a5925495fdf7e67 and independently verifies
SHA-256 of all 16 files before inference. CPU LiteRT inference is used.

## Native library separation

- RikkaHub's existing LiteRT 2.1.5 stays under SONAME libLiteRt.so.
- Scylla requires LiteRT 2.2.0. Its warmup failed on the Pixel with 2.1.5
  (g2p status 500), so a generic shared-library replacement is not safe.
- speech/scripts/stage_scylla_litert.py downloads Google 2.2.0 prebuilts,
  validates source SHA-256 and uses patchelf to set SONAME libScyllaLiteRt.so.
- CMake links Scylla's native runtime against the isolated SONAME.
- Generated binaries in speech/src/main/jniLibs are git-ignored. Build hosts
  need Python 3, patchelf, Android NDK 28.2.13676358, and first-build
  access to the upstream Google binaries. CI installs these requirements.
- Do not silently choose one library via pickFirst or upgrade RikkaHub's
  shared LiteRT 2.1.5 runtime as part of this TTS integration.

## Physical-device evidence (2026-10-12)

The isolated demo with both native runtime versions in one APK passed a real
Pixel 10 Pro LiteRT CPU synthesis (146,432 PCM samples in 1.29 s).
The RikkaHub TTS manager then passed its own acceptance test: 119,296
non-silent-capable PCM samples in two chunks, with a real terminal chunk.
A separate Gemma 4 E4B conversation regression after the integration passed
both turns: 12, then Photosynthesis.

These tests validate native speech, stream output and native coexistence.
They do not independently establish interactive settings-UI behavior or
background playback under Android battery management.
