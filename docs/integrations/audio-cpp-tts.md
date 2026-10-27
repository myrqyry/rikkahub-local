# audio.cpp: optional self-hosted speech provider

RikkaHub's `audio.cpp (Self-hosted)` TTS provider uses the actual audio.cpp
`POST /v1/audio/speech` API (documented by upstream
[server guide](https://github.com/0xShug0/audio.cpp/blob/v0.9.3/app/server/README.md)).
It does not install or start audio.cpp, change any existing speech preference,
substitute Fish Audio's hosted API, or fall back to another server.

## Configuration

1. On a machine hosting audio.cpp, start `audiocpp_server` with a configured
   TTS model. Confirm `GET /health` **and** `GET /v1/models`. A healthy
   server alone does not mean it has a working TTS model.
2. In RikkaHub's Speech provider settings add
   **audio.cpp (Self-hosted)**, then enter the server **origin** as reachable
   from the Android device (for example `http://DESKTOP-TAILNET-IP:8080`),
   the model's server **ID** from `/v1/models` (not the GGUF filename), and
   optionally its configured voice ID. The `/v1` path is appended by RikkaHub.
3. Opt into that provider. All previously configured providers remain
   unchanged. For a private setup, use an authenticated private network
   boundary or an explicit ADB reverse/port-forward; a desktop's
   `127.0.0.1` address is **not** the phone's desktop address.

The server must be reachable and access-controlled appropriately.
RikkaHub does not automatically expose the server on a LAN, enable a
Tailscale proxy, or provide additional server-side authentication.
Do not expose the unauthenticated speech endpoint publicly.

## Implementation scope

- The provider sends `model`, `input`, `response_format: "wav"`, and an
  optional `voice` to `/v1/audio/speech` using a bounded network request.
- It accepts a completed RIFF/WAVE response (at most 32 MiB), emits a single
  terminal `AudioChunk` with `AudioFormat.WAV`, and rejects HTTP failures or
  non-WAV data without masking failures by changing providers. Existing
  TtsSynthesizer and Media3 AudioPlayer consume WAV without format spoofing.
- This is **buffered** audio (not the separate audio.cpp streaming SSE/PCM
  protocol), which preserves existing playback behavior. Streaming should be
  designed and tested separately rather than pretending chunked WAV bytes
  are independently playable streams.
- No runtime/model weights are baked into the Android APK, and no Android
  app storage format or default provider has been changed. The provider
  setting is serialized as `audio-cpp`.

## Validation — 2026-10-26

- New fake-HTTP tests cover request method/path/payload, absence of invented
  API-key authentication, voice omission, WAV output format, HTTP errors,
  malformed/non-WAV response rejection. New settings tests cover discovery
  and serialization.
- Real desktop audio.cpp **v0.9.3** Vulkan runtime with a Kokoro 82M Q8
  package returned `GET /health` and `GET /v1/models`, then generated a
  real 24 kHz mono, ~5.05-second WAV over `POST /v1/audio/speech`.
- The opt-in `AudioCppLiveAcceptanceTest` exercised the **production Kotlin
  adapter** against that running HTTP server: **PASS, not skipped**. Run
  with `RIKKAHUB_AUDIOCPP_ACCEPTANCE=1 ./gradlew
  :speech:testDebugUnitTest --tests '*AudioCpp*Test'` while such a server
  is available on `127.0.0.1:8767`. Override
  `RIKKAHUB_AUDIOCPP_URL`, `RIKKAHUB_AUDIOCPP_MODEL`,
  `RIKKAHUB_AUDIOCPP_VOICE` for other instances.
- Full `:speech:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
  passed with the provider implementation. Subsequent focused tests also
  passed with the live server enabled.
- **Not yet proven:** new APK installed on a physical Pixel; phone-to-desktop
  transport; sound played in the Android app; RikkaHub streaming generation.
  These are separate acceptance gates. Do not call this phone-ready solely
  because the desktop and production JVM HTTP path work.

For Aster, podQast and HARMONIA, the shareable piece is the upstream
audio.cpp service contract, not duplicate synthesis implementations in
each app. Do not automatically introduce provider/UI changes to those
independent projects without inspecting their current consumer path.
