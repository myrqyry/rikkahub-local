# Nemotron 3.5 ASR GGUF — experimental Android backend

## Choice and scope

Prefer **transcribe.cpp + Q5_K_M GGUF** for the initial local Android speech backend. It is the only Nemotron path presently tested with actual English and Spanish inference on the Pixel 10 Pro, including the JNI bridge. ONNX INT4 remains a candidate for later comparative measurements; its Android end-to-end path has not been verified here. Do not equate GGUF with llama.cpp chat support: this is a separate FastConformer/RNN-T inference engine.

The existing Whisper LiteRT and cloud ASR providers remain available and unchanged. This is opt-in, CPU-only; no cloud fallback. The weights are downloaded only when requested (not bundled with the APK).

## Immutable sources and licensing

- Engine: `third_party/transcribe.cpp`, pinned Git submodule at `5700f127e38dd53c4735ea23ef743b4b538eacd4`.
- Weights: `handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf`, revision `53ce288f69326b415aeef030cfcb02a08a8096dd`.
- File: `nemotron-3.5-asr-streaming-0.6b-Q5_K_M.gguf`.
- Size: **559,647,200 bytes**.
- SHA-256: `86429e8c4f7fdcf9b3312269ad1ca6669478ba7805331c4aea7a2e33e9910d65`.
- NVIDIA weights: **OpenMDW-1.1**; check upstream terms before redistributing. App downloads are verified before promotion by `ModelInstall` (size + SHA). Manually imported models have no publisher identity proof.

## Execution and lifecycle

- Input: 16 kHz mono signed PCM16 from Android `AudioRecord`, converted to float32, non-overlapping 1,120 ms chunks, flushed on stop.
- Native: `NemotronNative` wraps transcribe.cpp `transcribe_open`, `transcribe_stream_begin`, `transcribe_stream_feed`, `transcribe_stream_get_text`, `transcribe_stream_finalize`, `transcribe_close`. It explicitly requests CPU; the backend is logged from native runtime evidence.
- Default right context: **3** frames. UI options: 0/3/6/13. The model's streamed hypothesis can revise; the adapter sends the *entire* current text each time so `ChatInput` replaces its transcription suffix, rather than appending duplicates. This does not expose a separate committed/tentative event contract yet.
- Capture is separate from the single native worker, with a bounded chunk channel. A full queue raises an error rather than silently dropping speech. Teardown stops capture, cancels the worker, and frees the native session. A missing model never falls back to cloud.
- CMake compiles transcribe.cpp/ggml **statically into `libnemotron_asr_android_jni.so`**, hiding static symbols. The app already packages a distinct ggml runtime: DO NOT reintroduce shared `libggml*.so` from this submodule or add a packaging `pickFirst` to conceal ABI duplication.
- Android Gradle Debug native inference targets alone compile with `-O2`. Unoptimized Debug sources caused grossly misleading streaming latency (~90 s for 10.56 s speech in the standalone JNI harness); do not remove this without re-benchmarking. Other app debug targets remain unaffected.

## Device acceptance evidence — 2026-11-02

Hardware: Pixel 10 Pro (`blazer`, Android 17, arm64-v8a), CPU-only. All tests used the exact Q5 GGUF; download SHA and length were checked on Linux before transferring it to the isolated device test directory. Native source and sample WAVs came from the pinned transcribe.cpp checkout.

| Path | Language | Audio | Observed processing |
| --- | --- | ---: | ---: |
| Linux native CLI / Ryzen 7 7700 | en-US | 10.56 s | 497 ms inference (~21x realtime) |
| Linux native CLI / Ryzen 7 7700 | es-ES | 9.72 s | 380 ms inference (~26x) |
| Pixel native CLI offline | en-US | 10.56 s | 2,578.8 ms inference (~4.1x) |
| Pixel native CLI offline | es-ES | 9.72 s | 2,431.3 ms inference (~4.0x) |
| Pixel native CLI streaming 1,120 ms/R=3 | en-US | 10.56 s | 6,273.5 ms processing (~1.7x); live partials and final generated |
| Pixel isolated Java `app_process` using **actual Gradle-built JNI** (`-O2` inference), exact-size chunk feed | en-US | 10.56 s | 9 s *wall-clock* including load, Java process and streaming; native backend reported CPU; successive partials and correct final |

The sample utterances ended with the English transcription “However, due to the slow communication channels styles in the West could lag behind by twenty five to thirty years.” (streamed) and Spanish “Fue tanta la cantidad de gente que se concentró que no todos pudieron acceder al funeral en la Plaza de San Pedro.” (offline). Streaming may change punctuation relative to offline.

Test limits: **a full microphone-to-chat flow inside the installed RikkaHub app has NOT yet been executed.** Gradle compilation, native linking, JVM tests, debug APK assembly, and an isolated-device JNI load/feed/finalize are confirmed. No installation, user model deletion, cloud transcription, GPU, or production-duration/thermal soak was performed. Recheck these before claiming default-ready ASR.
