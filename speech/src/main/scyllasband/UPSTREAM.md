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

## Shared native LiteRT 2.3.0 upgrade

- RikkaHub and Scylla both link the exact LiteRT 2.3.0 binary from Google's
  Maven AAR, using the matching v2.3.0 C++ SDK release headers.
- The upgrade removes the temporary separate LiteRT 2.2.0 ELF SONAME
  introduced while RikkaHub was pinned to 2.1.5. That older combination was
  necessary because Scylla G2P failed with 2.1.5 (status 500) on the Pixel.
- scripts/stage_litert_230.py verifies both the Maven AAR and C++ SDK archive
  using SHA-256, stages the two production ABIs, and is invoked before
  app/speech CMake native builds. No binary downloads are committed to git.
- LiteRT-LM 0.18.0 is a separate engine and has its own tests. Legacy
  TensorFlow Lite Interpreter v2.16.1 migration remains separate.

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

## Borrowed synthesis controls from Wyoming bridge — 2026-10-13

Compared https://github.com/allenbenz/scyllasbandv2-wyoming at
`ad96c5e59741c3b6cf668d525e62cb1958bb457e`, an ONNX/Wyoming/Home
Assistant adaptation. We did **not** introduce its server, ONNX runtime,
Docker installation, model weights, or Wyoming protocol into Android.

Transferred just its useful user-configurable **synthesis parameters**:
- Model speaking-speed scale (0.5–1.5), native `ScyllasBandRequest.speed`;
- Model flow-step override (0 = bundle default; Android UI 0–16), native
  `ScyllasBandRequest.steps`;
- Euler / Heun / bundle-default sampler selection through the public C enum.

These are backed by the already-vendored Scylla native C++ and Android JNI.
Defaults remain speed=1.0, steps=0 and sampler=DEFAULT for saved providers.
The model remains the same SHA-verified 164 MB LiteRT bundle and LiteRT 2.3.0.

**Why other concepts weren't transplanted:** The Wyoming adapter cuts audio into
~200-ms network frames. RikkaHub's current TtsSynthesizer collects an entire
utterance to `TTSResponse` before AudioPlayer invokes ExoPlayer, so smaller
`AudioChunk` packets *alone* cannot improve first-audio latency. Real live
playback would require a separately validated playback-architecture change.
Wyoming's `whisper` flag isn't available through the native Scylla implementation
(the vendored C++ explicitly rejects it). V1's prefix-latent carryover and
emotion axes are a different model/version, not options of our v2 LiteRT graph.

**Physical proof:** Pixel 10 Pro passed `ScyllaTtsProviderDeviceTest` with the
same Ink/en_gb sentence and deterministic seed, Euler+4 steps at speed=1.2
(135,168 bytes) and speed=1.0 (165,888 bytes), then Heun+4 steps at speed=1.0
(165,888 bytes, real PCM). Native engine session resets and terminal chunk
checks passed. :speech:testDebugUnitTest 97/97; :app:testDebugUnitTest 1458/1458;
:app:assembleDebug and :app:assembleDebugAndroidTest passed. Compose controls
compiled, but their on-screen tap flow and perceptual quality weren't separately
verified. This is not an empirical claim that Heun is always better than Euler.
