# MediaPipe Parakeet ASR — Android integration (2026-11-04)

## Scope and rationale
An opt-in local Android speech provider for MediaPipe `AudioTranscriber` 1.2.2. The existing Whisper LiteRT, Nemotron GGUF, and all cloud providers are left intact. App voice input uses the same `ASRController` interface as existing providers; no new global ASR selection policy.

This native implementation uses real `AudioTranscriber.createFromOptions(context, options)`, `transcribe(FloatArray, false)`, `finishStream()`, `prewarm()`, and `close()` from the resolved Android 1.2.2 AAR. It streams 16-kHz mono PCM through an `AudioRecord` capture coroutine and a bounded 32-element channel on a separate inference dispatcher. On queue overflow or microphone error, it explicitly reports failure; it does not drop audio silently or substitute cloud transcription.

## Model and storage
- Weights: NVIDIA Parakeet TDT 0.6B v3, `litert-community/parakeet-tdt-0.6b-v3`, revision `1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd`.
- Filename: `parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`
- LFS size: 614,588,752 bytes; SHA-256: `66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504`.
- The model is never bundled or fetched automatically. The Settings → Speech provider download action calls existing `ModelInstall.download`, preserving its partial/resume, SHA/size check, and verified promotion. Downloads require model size + 256 MiB free-space reserve.
- Existing installed app data/settings/models are unchanged; only an explicitly selected provider path is used. This pass did not install an APK, remove anything from the phone, or initiate a weight download.

## Verification and limits
- AAR methods were independently inspected from the actual Google Maven artifact.
- `ParakeetMediaPipeTest` tests model pins, polymorphic serialization, and overlapping interim/final segments.
- Focused `:speech:testDebugUnitTest --tests me.rerere.asr.ParakeetMediaPipeTest` and `:app:compileDebugKotlin` passed. Full `:speech:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` passed after one initial `packageDebug` failure. An immediate isolated `packageDebug --stacktrace` retry also passed, followed by a successful combined rerun (317 tasks). Initial packaging failure's root cause was not established; the final debug APK contains arm64/x86_64 MediaPipe JNI libraries.
- **Not device-qualified:** no real phone inference, permission-cycle smoke, RAM/latency measurement, model-load proof, exact engine-vs-system LiteRT ABI verification, or cross-language quality test. Full APK build can verify packaging but not native model execution. Existing `libomp.so` native merge warnings remain and may be significant to device testing.
- MediaPipe streams result segments; app shows concatenated text only (not word-level timestamps or diarization). This is not a replacement for Droplette's beat-aware lyric alignment.

## Device acceptance
Only after checking free storage and choosing to download the model: use the existing Android debug APK in-place update path with backups where appropriate. Test repeated mic start/stop, partial/final transcript consistency, explicit provider switch while capturing, failure when queue is full, speech after locking screen, Spanish/English utterances, and CPU/thermal/memory behavior. Do not claim working Android inference from JVM tests or successful APK construction.
