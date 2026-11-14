# MediaPipe local TTS: Phonon 31M and Kokoro 82M

## Scope and integration

The November 2026 local speech work adds two **opt-in** TTS provider types:
`PhononTts` and `KokoroTts`, with distinct serialized identities and
settings. Both route through the shared `MediaPipeTTSProvider` using the
Android `com.google.mediapipe:tasks-audio:1.2.2` TextToSpeech API. The
model manager accepts either a user-selected local bundle or an explicit
download. No fallback to cloud speech is introduced.

The catalog in `speech/src/main/java/me/rerere/tts/mediapipe/MediaPipeTtsCatalog.kt`
pins the filenames, byte sizes, SHA-256 digests, voices and source links:
- Phonon: `phonon_int8.litertlm`, 87,205,888 bytes; default `freya`.
- Kokoro: `kokoro_int8_fp16.litertlm`, 294,783,298 bytes; default `af_heart`.

The app verifies file size before use and the pinned SHA-256 before
starting the engine. The UI's size-only installed badge is **not** proof
of authenticity; verification occurs when importing or executing.
Audio is converted from normalized floating-point samples to
24 kHz signed little-endian PCM16. The existing PCM streaming player
is selected only when voice effects are disabled. Existing speech
providers and settings remain intact.

## Verification and outstanding acceptance

Verified locally on the existing `aster/mediapipe-parakeet-asr` checkout,
November 13, 2026:
- `:speech:testDebugUnitTest`: **151 tests, zero failures or errors**.
- `:app:compileDebugKotlin`: succeeded.
- `:app:compileDebugAndroidTestKotlin`: succeeded, including the
  `MediaPipeTtsProviderDeviceTest` probe sources.
- These checks establish compilation, serialization and deterministic
  audio conversion; they do **not** establish actual native model inference,
  elapsed time, audible playback, Bluetooth performance or thermal behavior.

Real-device instrumented tests for both provider paths and an additional
Phonon probe are present under `app/src/androidTest/`. They have **not
been executed in this integration pass**: there was no connected adb
device when checked. Do not report either model as device-qualified
until its actual pinned bundle loads, returns non-silent PCM and the
human-audible output is separately verified.

No model was downloaded, no installed app data was reset, and no
production provider selection was changed as part of this integration.
Source provenance remains with the existing pinned MediaPipe SDK
dependency and the linked Hugging Face publisher repositories.
