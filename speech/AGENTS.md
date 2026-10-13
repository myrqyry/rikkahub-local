# Speech — Voice & Speech

## What Lives Here

Speech-to-text, voice recording, and audio processing for voice input in chat.
Local TTS providers include Pocket, Kitten, Qwen3-TTS, Matcha-TTS, and Scylla's Band; the
LiteRT-backed providers use models downloaded into the app-managed local model
directory.
Whisper LiteRT ASR supports catalog-backed Base and Tiny model variants plus
manual custom `.tflite` imports.

## Key Files

| File | Purpose |
|------|---------|
| `src/` | STT engine, audio capture |
| `build.gradle.kts` | Module dependencies |

## Deviations from Root

- No deviations — follow root conventions.

Scylla shares the pinned LiteRT 2.3.0 Maven C native runtime with RikkaHub's
CompiledModel consumers; :app:stageLiteRtSdk provides matching C headers.
See src/main/scyllasband/UPSTREAM.md for provenance and compatibility gates.
