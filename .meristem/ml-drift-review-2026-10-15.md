# ML Drift GPU feasibility — 2026-10-15

Upstream: https://github.com/google-ai-edge/ml-drift
Inspected main tree: 6e803da7d529ce1f4bd35fdbd58dcf8460029e86.
License: Apache-2.0.

## What the source supports

Google AI Edge describes ML Drift as the successor of the TensorFlow
Lite GPU delegate. It is a low-level C++ GPU compute graph engine,
not a new ONNX/TFLite filename extension or an immediately swappable
runtime for RikkaHub's existing LiteRT/ONNX executors.

A backend-neutral GpuModel stores operation graph nodes/tensors;
the GpuModelBuilder includes fully connected, attention-related,
convolution, quantized weights, RoPE and other operators. Kernels
can be specialized for device/shape; intermediate storage is reused
across disjoint tensor lifetimes. OpenCL is the documented primary
Android path; OpenGL ES and WebGPU/Dawn also exist. CMake currently
supports cl, gl and wgpu (not Metal despite the top-level overview).

Source locations:
https://github.com/google-ai-edge/ml-drift/blob/main/README.md
https://github.com/google-ai-edge/ml-drift/blob/main/docs/hello_world_cl.md
https://github.com/google-ai-edge/ml-drift/blob/main/ml_drift/CMakeLists.txt
https://github.com/google-ai-edge/ml-drift/blob/main/ml_drift/common/gpu_model_builder.h
https://github.com/google-ai-edge/ml-drift/blob/main/ml_drift/cl/memory_manager.h
https://github.com/google-ai-edge/ml-drift/blob/main/ml_drift/cl/opencl_wrapper.cc
https://github.com/google-ai-edge/ml-drift/blob/main/ml_drift/cl/cl_device.cc

OpenCL loader explicitly tries vendor libOpenCL.so and Pixel-specific
libOpenCL-pixel.so, including a special Pixel enableOpenCL wrapper.
PowerVR/DXT device classification appears in its GPU information code.
The README's claimed order-of-magnitude speedup is not verified
here. No turnkey ONNX or TFLite file import into GpuModel was
demonstrated by this source review.

## Actual Pixel 10 Pro device probe

Connected ADB device 56290DLCH002PE (Pixel 10 Pro, board laguna).
Observed native vendor paths libOpenCL.so and libOpenCL-pixel.so,
and both names listed in /vendor/etc/public.libraries.txt.
This suggests app loader access but does not prove that a loaded
RikkaHub JNI library can initialize the engine inside the app process.

Compiled an independent, header-free ARM64 OpenCL probe with
NDK 28.2.13676358, Android API 28. The initial isolated binary
failed due to missing libc++_shared.so; rebuilding with
-static-libstdc++ succeeded.

Verified result in /data/local/tmp (not installed application):
- Loaded OpenCL symbols via libOpenCL.so
- Exactly one platform and one GPU (both query status 0)
- PowerVR D-Series DXT-48-1536 by Imagination Technologies
- OpenCL 3.0, including the cl_khr_fp16 extension
- Built, dispatched and read back a real 256-element GPU kernel:
  output[i] = 2 * a[i] + b[i]
- Maximum absolute output error 0 against the CPU reference
- Exit status 0, temporary executable removed

Reproducible, independently authored diagnostic:
scripts/probes/android-opencl-smoke.cpp
scripts/probes/run-android-opencl-smoke.sh

Usage on Linux with NDK and an authorized adb device:
ANDROID_NDK_HOME=/path/to/ndk scripts/probes/run-android-opencl-smoke.sh

## Applicability and limitations

- RikkaHub Local already has measured LiteRT backend selection,
  real available-memory admission, and local runtime/SoC checks.
  A source-integrated ML Drift backend would require a real GpuModel,
  actual model weights and a tested Android native adapter; do
  not show a fake additional GPU option.
- Voxa's RVC/ContentVec/RMVPE ONNX pipeline is NOT made runnable
  merely because the Pixel supports OpenCL. Porting operations
  and conversion of model graph/weights would be substantial
  engineering. The previous failed on-device CPU RVC experiment
  remains authoritative.
- Image generation may be a better narrow test of custom graphs,
  but only after a real model-specific graph is supported.
- The upstream memory-reuse and per-stage inference optimizations
  are interesting mechanisms, not verified benefits for our models.

This test proves only shell-namespace OpenCL GPU execution. It does
NOT prove ML Drift was compiled/tested, app-process JNI access,
model import/conversion, real inference output, energy, thermals,
latency, cancellation, or a performance improvement over LiteRT.

## Next gated steps

1. Reproduce the official add_test_opencl Android unit-test build
   and device test from upstream docs. Bazelisk is not yet installed
   on the host; do not silently install a toolchain or claim a build.
2. Run a tiny ML Drift GpuModel through its real OpenCL inference
   context and compare checked output against a CPU reference.
3. Validate app-process native library loading on a disposable test
   build without destroying installed app data.
4. Choose one representative model/operation and benchmark numerical
   parity, cold/warm latency, memory, thermal and cancellation
   against the existing runtime before any backend promotion.

No ML Drift source was copied. No upstream merge, Pixel app install,
model download, change to existing model/runtime, or push.
