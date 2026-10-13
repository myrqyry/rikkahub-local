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

Scylla uses a separately renamed LiteRT 2.2.0 ELF SONAME so it can coexist with
RikkaHub's LiteRT 2.1.5. See src/main/scyllasband/UPSTREAM.md. Never merge
or pick-first the two native versions by filename.
