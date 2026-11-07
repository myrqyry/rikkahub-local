# Incremental Pocket TTS PCM playback — 2026-11-06

## Goal

Actually begin audio playback while the synthesizer is still producing frames, rather than conflating incremental synthesis with playback. Preserve the buffered path for compressed providers and optional whole-buffer voice effects.

## Current behavior and implementation

- Before: `TtsSynthesizer.collectToResponse` accumulated Pocket's 24-kHz PCM16 frames in a `ByteArrayOutputStream`, and `AudioPlayer.play` received only the completed utterance.
- Now: with Pocket selected and voice effects OFF, `TtsController` sends the provider's `Flow<AudioChunk>` straight to `AudioPlayer.playPcmStream`, which initializes a mono PCM16 `AudioTrack` on its first frame and performs nonblocking, coroutine-cancellable writes as subsequent frames arrive.
- `PcmStreamPump` validates the format, sample rate, alignment, and final markers; it does not accumulate the utterance.
- The old buffered ExoPlayer path remains for other providers and for Pocket when a full-buffer voice effect is selected. Pause/resume/speed/stop are routed to the active output; seeking live unbuffered PCM is intentionally unsupported rather than seeking an inactive ExoPlayer.
- A finished producer is not treated as finished audible playback: the output drains before reporting Ended. Pauses do not count as a drain stall.
- No new model downloads, provider setting migrations, device installs, or changes to saved state.

## Evidence

- RED first: `PcmStreamPumpTest` failed to compile before the implementation existed.
- Focused JVM tests passed after implementation, including immediate frame delivery before the next producer emission, changing sample rate, malformed alignment, non-PCM data, and cancellation propagation.
- `./gradlew :speech:testDebugUnitTest :app:assembleDebug --offline`: successful (302 tasks).
- `./gradlew :app:testDebugUnitTest --offline`: see subsequent verification receipt / active work.

## Limits / next steps

- Android `AudioTrack` output has **not been listened to on the Pixel**. The JVM tests establish stream consumption and input contracts, not real latency, underruns, Bluetooth resilience, focus/background behavior, or device audio correctness.
- Model initialization/first-frame latency remains independent from playback latency.
- MediaPipe TTS is not yet a provider. Its `tasks-audio:1.2.2` types are present in the cached AAR; integrate separately against the verified signatures/model bundle and opt-in model management.
- Genuine *LLM token → audio* streaming is also separate: the current `speak(text)` interface accepts chunks of supplied text, not a live token-flow contract.
- The Pocket streaming fast path currently bypasses whole-buffer voice effects by design. A stateful streaming effect implementation would be a separate change.
