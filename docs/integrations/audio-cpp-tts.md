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

## Ollama Speech compatibility — 2026-10-26

Ollama introduced a compatible **`POST /v1/audio/speech`** endpoint
in v0.40.8 (published speech guide recommends v0.40.9+).
Its Phonon model is currently **Apple Silicon only**; Linux and
Windows support is not released. The desktop Ollama servers checked
here are v0.30.10 and v0.35.1 and all return HTTP 404 from
`/v1/audio/speech`. Do **not** update production Ollama solely to
install an unsupported speech model.

RikkaHub's existing **OpenAI TTS** provider now supports `responseFormat`
`mp3` (old default) or `wav`, sending the chosen format unchanged.
When an Ollama speech-capable server is available, select **OpenAI TTS**
with its URL ending in `/v1` (e.g. `http://DESKTOP-TAILNET-IP:11434/v1`),
model `phonon`, voice `default`, blank API key, and response format
**WAV**. The provider omits the Authorization header when the key is blank.
The server must be reachable from the device through an authorized
private route; desktop localhost isn't phone localhost. A configured
model must report `speech` capability; chat-model registrations are not
proof of speech support.

This reuses the same buffered `AudioChunk`/Media3 playback boundary
rather than adding an Ollama-only engine. It does **not** yet play audio
while the WAV is streaming; Ollama's streaming WAV header uses
placeholder length fields. The JVM adapter request/WAV handling is
unit-tested against simulated response bytes. The opt-in
`OpenAITTSSpeechCompatibilityTest` also **passed against the live
audio.cpp Kokoro server** using the production OpenAI-compatible
provider and returned real WAV audio. No Phonon real-model generation
or physical-phone playback has been demonstrated.

The working Linux speech backend remains audio.cpp v0.9.3 with Kokoro.
No service settings, app installation, or network bindings were
changed as part of Ollama compatibility.
