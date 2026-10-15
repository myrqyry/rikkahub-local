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
