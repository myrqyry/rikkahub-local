# Speech — Voice & Speech

## What Lives Here

Speech-to-text, voice recording, and audio processing for voice input in chat.
Local TTS providers include Pocket, Kitten, Qwen3-TTS, and Matcha-TTS; the
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
