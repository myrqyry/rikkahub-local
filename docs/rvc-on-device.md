# RVC post-TTS on Android — native inference requirement

Status (2026-10-14): **Android-native runtime startup verified; voice conversion not yet implemented or verified in RikkaHub.**

## The requirement

RVC inference **runs on the Android device**, with no Voxa server or network request.
It processes synthesized TTS audio after the existing provider produces it and before
RikkaHub plays it. Voice checkpoint/model files are installed on the device.
The selected model should remain warm during nearby phrases and release resources
after an idle TTL, on cancellation, or under memory pressure.

The previously implemented desktop Voxa HTTP postfilter (commit `334195ba`)
did not meet this requirement. Its source changes were reverted in the next
corrective commit; the original commit is preserved in history. Do **not**
describe that network path as device-local RVC.

## Evidence from actual Android port spike

- `speech` already links **ONNX Runtime Android 1.25.0** and LiteRT 2.3.0,
  but there is **no complete RVC/ContentVec/RMVPE/synthesizer ONNX chain**
  currently installed or exercised on the phone.
- The local `audio.cpp` checkout at `26b8927` (Apache-2.0) includes
  native RVC inference with HuBERT, RMVPE, an RVC synthesizer and
  optional retrieval index, exposed through a C ABI (`include/audiocpp.h`).
- The RVC-only Android arm64 C ABI and CLI both **cross-compiled** using NDK
  28.2.13676358, Android API 28, CMake/Ninja, with
  `AUDIOCPP_MODEL_SET=custom`, `AUDIOCPP_MODELS=rvc`,
  `AUDIOCPP_BUILD_C_API=ON`, and deployment specs embedded.
- First build required `libomp.so` at runtime despite
  `ENGINE_ENABLE_OPENMP=OFF`. Reconfiguring with
  `GGML_OPENMP=OFF` removed that dependency. Rebuilt binary needed only
  standard Android `libm`, `libdl`, `libc`.
- The rebuilt CLI **executed on the Pixel 10 Pro** and returned one valid
  CPU backend from `--list-devices`. The temporary binary and OpenMP
  helper were removed after the check.
- This is **not** an RVC inference test: no GGUF, voice checkpoint or source
  waveform was loaded on the Pixel, so synthesis/output/latency is unknown.
  No server or model was added to the phone.
- The readily available self-contained RVC F16 GGUF in
  `~/loam/audio.cpp/models/RVC-GGUF/rvc-f16.gguf` is approximately 1.2 GiB,
  while `adb shell df -h /data` reported about **7.4 GiB free**.
  Do not transfer this package automatically just to run a smoke test.

Reproducible isolated build (requires local `audio.cpp` checkout and Android NDK):

```sh
cmake -S /path/to/audio.cpp -B /tmp/audio-rvc-android -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=/path/to/ndk/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DAUDIOCPP_MODEL_SET=custom -DAUDIOCPP_MODELS=rvc \
  -DAUDIOCPP_DEPLOYMENT_BUILD=ON -DAUDIOCPP_BUILD_C_API=ON \
  -DENGINE_ENABLE_OPENMP=OFF -DGGML_OPENMP=OFF \
  -DENGINE_ENABLE_LLAMAFILE=OFF -DENGINE_ENABLE_NATIVE_CPU=OFF
cmake --build /tmp/audio-rvc-android --target audiocpp audiocpp_cli -j 4
```

## Integration sequence (uncompleted)

1. **Choose the supported model bundle**, with a reproducible provenance,
   signature/input contract, license and reasonable storage/RAM footprint.
   The native `audio.cpp` package is a proven ARM64 *build* candidate, not a
   verified usable phone model. Another viable design is fully on-device
   Android ONNX Runtime with an exported RVC Synth + HuBERT/ContentVec +
   RMVPE trio; user `.pth` checkpoints need conversion **ahead of time**,
   not server inference during playback.
2. Build the C ABI as a pinned reproducible Android dependency (or implement
   the complete ONNX pipeline). Do not depend on a developer's absolute
   filesystem path, ship a binary without source provenance, or copy a third
   party's unlicensed source into RikkaHub.
3. Expose model-import/validation and choice through local SAF / model manager.
   No server URL. Load each model once, release on a configurable bounded idle
   TTL and cancellation/memory pressure.
4. Convert the in-app TTS chunks to the required native PCM input, run *local*
   HuBERT → RMVPE → synthesizer, and return a verified WAV/PCM output to
   `TtsController`. Do not fake success by returning the original audio or
   talking to a remote service.
5. Instrument **real** on-device inference with a known RVC voice and PCM
   sample, compare audible/non-silent output and report cold/warm latency,
   model RAM/storage consumption, cancellation, and failure behavior.
   Only enable the Settings switch after these checks actually pass.

## Other references

`ouor/RVC-Android` publicly documents the Android ONNX tensor contracts and
warm sessions, but has no license file in the inspected checkout as of this
probe. Treat it as reference documentation only; do not copy its source.
`Sharl210/ultimate-rvc-mobile` also documents on-device RVC, with its own
exported ONNX model requirements and limitations.

The user explicitly corrected the architecture on 2026-10-14.

## 2026-10-14 — Real GGUF voice conversion feasibility tests

**Outcome:** The native `audio.cpp` CPU-only RVC pipeline is **not suitable
for responsive phone TTS in the configuration tested**. The Android CLI
started and read a verified local RVC model package, but neither phone run
completed a conversion before its safety deadline. There is no evidence of
successful converted audio on Android.

Controlled source: a 5.58-second synthetic mono WAV at 22,050 Hz
(`/tmp/rikkahub-rvc-smoke.wav` on the desktop). Model: unchanged
`RVC-GGUF/rvc-f16.gguf`, 1,260,188,416 bytes, SHA-256 verified after
transfer to the Pixel. Voice: packaged `default` with no retrieval.

- **Desktop CPU control (same model):** RVC-only host CLI completed a real
  voice conversion in **25.50s** cold at 3 threads; maximum measured resident
  memory **1,798,756 KiB**. Converted output: 5.56s mono 40,000 Hz PCM16 WAV,
  435 KiB, RMS ≈0.0842, non-silent. Confirms model and inference path work
  on the desktop, but not interactive-speed even there in this CPU test.
- **Pixel baseline arm64 build:** CPU backend, 2 threads, nice 15,
  default NDK feature detection (FP16 vector arithmetic and ARM dot-product
  disabled). Timeout **90s**; no WAV output.
- **Pixel optimized arm64 build:** C and C++ flags
  `-march=armv8.2-a+fp16+dotprod`; CMake correctly detected ARM dot-product
  and FP16 vector arithmetic. Android linker required only standard
  `libm`, `libdl`, `libc`; CPU enumeration succeeded on Pixel. CPU backend,
  2 threads, nice 10. Timeout **135s** (wall clock ≈136s); no WAV output.
  Process remained active until stopped by the timeout, reached ≈1.2 GB RSS.
- Both Android experiments copied the model only to `/data/local/tmp`,
  ran low priority and bounded, then removed their model, executable, input
  and output files. The final check found no `rvc-local-smoke-*` artifacts;
  RikkaHub still had a process and the installed package timestamp was
  unchanged. No on-device app data, provider settings, or models were altered.
  Phone free storage fluctuated from ~7.4 GB before to ~7.0 GB after
  (Android/system background changes not attributed to the test).
  No claim of full storage recovery beyond verified test-file removal.
- During the last check the Pixel reported ≈4.6 GB available RAM and
  battery temperature ≈31.3°C. No reported thermal crisis.
- Logs reside only on the desktop under `/tmp/rvc-device-smoke-master.log`,
  `/tmp/rvc-device-optimized-master.log`, and
  `/tmp/audio-rvc-host-infer.log`; these paths may be ephemeral.

**Next choice:** Do not install the RVC GGUF CPU-only runtime in RikkaHub as
a functional voice filter. Investigate a *smaller exported RVC ONNX chain*
(synth + ContentVec/HuBERT + RMVPE) and benchmark Android session reuse and
suitable delegates before UI work. A device-local idle TTL is appropriate
only after actual reusable inference sessions exist. A CPU cold-load timeout
is not proof of defective models or proof that GPU/delegate acceleration
would succeed; those are untested alternatives.

## 2026-10-14 — Isolated Pixel 10 Pro ONNX stage feasibility benchmark

**Status:** All three selected real ONNX model stages loaded and executed on
the Pixel under a separate test APK, but **no complete converted voice audio**
was produced or listened to. This is a *feasibility measurement*, not a
completed or usable RVC feature.

The isolated app uses the same ONNX Runtime Android version (1.25.0) as
RikkaHub but a different application ID. It measures real INT8 ContentVec,
real INT8 RMVPE and a real FP32 RVC v2 voice synthesizer. Inputs were
synthetic stage tensors, NOT an end-to-end TTS waveform passed through all
three models. The model files total 309,070,717 bytes. All three SHA-256
digests matched after transfer. Model revisions, licenses, full hashes,
reproduction and raw device metrics are preserved in
tools/experiments/rvc-onnx-probe.

Stage input / session load / first / warm 1 / warm 2:
- ContentVec Q8 on 2.00s of 16k synthetic waveform:
  378ms / 816ms / 630ms / 592ms. Output tensor [1,99,768].
- RMVPE Q8 on [1,128,128] synthetic mel tensor:
  651ms / 608ms / 496ms / 493ms. Output tensor [1,128,360].
- Nova_HD 48k RVC v2 synth on 96 synthetic feature/F0 frames:
  434ms / 1570ms / 1410ms / 1398ms. Output tensor 46,080 samples,
  corresponding to 0.96 seconds at 48k (no audio quality validation).

Synthesis provider comparison (same model and synthetic input):
- CPU: load 434ms, warm 1410 / 1398ms.
- XNNPACK: load 491ms, warm 1416 / 1403ms.
- NNAPI: load 533ms, warm 1434 / 1399ms.
- WebGPU: load 496ms, initial inference 10695ms,
  warm 4785 / 4724ms (significantly slower).

Each model/provider JUnit test returned OK (1 test). NNAPI availability
and session creation **do not prove offloading to the Tensor NPU**; near
identical timings suggest little effective benefit for this graph.
WebGPU availability did not imply improved acceleration.

**Performance conclusion:** This chosen RVC synth takes ~1.4 seconds to
produce 0.96 seconds of expected audio even after warming. Its throughput
is worse than real time on all tested Android ORT providers. Adding encoder,
pitch, resampling, mel extraction, audio alignment and crossfades can only
increase work. The normalized stage totals roughly suggest compute cost
greater than twice audio duration, but **this is NOT a measured full-pipeline
RTF**. No network service and no mock conversion were used.

**Remaining work / distinct alternatives:**
1. For existing RVC voice-model compatibility, test a genuinely smaller
   256-dimensional RVC v1 synthesizer or a verified low-compute export;
   require a license permitting use in RikkaHub. Do not use the small
   w-okada sample voice as a shortcut: inspected model terms restrict usage
   to VCClient.
2. ONNX-to-TFLite conversion might expose a faster LiteRT delegate but is
   NOT validated. The RVC voice model includes dynamic axes and numerous
   Conv/Pad/Transpose-related operators; conversion success and actual
   supported delegated graph partitions must be demonstrated separately.
   Merely producing a .tflite file is insufficient.
3. Beatrice-V2 is a different, potentially much smaller voice-conversion
   architecture (~20 MB of model assets in an inspected MIT example). Its
   model bundles cannot simply load existing RVC .pth voices, and on-device
   end-to-end quality/latency have not yet been established on the Pixel.
   Android research repo InkRoad/mobRealVoice targets rooted system-audio
   routing and has no project license; do not copy it into RikkaHub.
4. ouor/RVC-Android is a useful Android ONNX pipeline reference with
   warm sessions, but the inspected checkout had no license file. Do not
   copy its implementation as though the source were licensed.
5. MeowRVC contains real ORT calls yet also a placeholder native file
   and broad input-fallback behavior. Its marketing claims alone do not
   establish correct, fast end-to-end Android inference.

**Gate for shipping:** real, fully local end-to-end conversion of a known
2- and 5-second TTS sample; verify WAV/sample rate, audible intelligibility
and converted voice; measure warm RTF below 1x (ideally much better),
RAM/storage, cancellation, backpressure and 5-minute idle TTL across
repeated conversions. Until proven, DO NOT add a production RVC toggle.

The test APK was never RikkaHub. After benchmarking, both isolated APK
packages and all ~309MB of temporary model copies were uninstalled/
deleted. Package inspection found no remaining probe packages; existing
RikkaHub PID was still alive and phone free storage ~7.0GB. No production
RikkaHub code or on-device user app data was changed.
