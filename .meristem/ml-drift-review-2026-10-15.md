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

## Verified official Android tests (2026-10-15 evening)

Pinned temporary upstream checkout: /tmp/ml-drift-investigation-20261015,
commit 6e803da. Downloaded Bazel 8.0.0 only into /tmp and verified
the publisher SHA-256:
18417fdee645a8dc08bc3f27e33f71a5681f9f14661b33ac48536a31dc8d00f1.
Used installed Android NDK 28.2.13676358. No system packages installed.

### Important silent-build trap

The unmodified upstream Bazel config --config=android_arm64 returned
BUILD SUCCESSFUL but produced an x86-64 Linux/glibc executable
despite its arm64-v8a output path. Inspection with file/readelf
and Bazel aquery revealed it was linked by /usr/bin/gcc.

Two compatibility issues were isolated in temporary sources:

1. The custom third_party/android_ndk.bzl imports a Bazel helper
   no longer available at @bazel_tools//tools/build_defs/repo:repo.bzl,
   then references a rules_android_ndk path absent in the downloaded
   dependency. Replaced its MODULE.bazel block in the isolated ML Drift
   clone with the dependency's published extension:
   use_extension("@rules_android_ndk//:extension.bzl",
   "android_ndk_repository_extension"),
   configure(api_level=28), use_repo(...,"androidndk"), and
   register_toolchains("@androidndk//:all").
2. Added a temporary BUILD platform target experimental_android_arm64,
   with @platforms//os:android and @platforms//cpu:arm64 constraints.
   Selected --platforms=//:experimental_android_arm64 during builds.
3. The downloaded rules_android_ndk template BUILD.ndk_clang.tpl has
   an all_binaries glob containing lib64/**/* with default
   allow_empty=False, but NDK 28 has no legacy lib64 at that location.
   A temporary copy of the dependency was changed to allow_empty=True
   for only that glob, selected via local_path_override. No installed
   Android NDK files or upstream remote files were edited.
4. Verified with Bazel aquery that linking now invokes NDK clang
   with --target=aarch64-linux-android28, never /usr/bin/gcc.

Actual corrected target builds in that isolated checkout:

    bazel --output_user_root=/tmp/ml-drift-bazel-output --batch build -c opt --config=android_arm64 --platforms=//:experimental_android_arm64 --jobs=4 //ml_drift/common/kernels/tests:add_test_opencl
    bazel --output_user_root=/tmp/ml-drift-bazel-output --batch build -c opt --config=android_arm64 --platforms=//:experimental_android_arm64 --jobs=4 //ml_drift/cl:inference_context_test

The first true Android build completed with 591 actions in 53.8s
(previous action cache reused); the second completed with 98 actions
in 37.7s. Both binaries are real AArch64 ELF, use
/system/bin/linker64, and depend only on Android system libraries.

### Pixel 10 Pro verified results

| Official test | Total | Passed | Skipped | Failed | Elapsed |
| --- | ---: | ---: | ---: | ---: | ---: |
| OpenCL add-kernel | 162 | 108 | 54 | 0 | 4.576 s |
| GpuModel inference context | 2 | 2 | 0 | 0 | 0.389 s |

The 54 skipped tests explicitly report unsupported tensor storage
formats/layouts. Do not count those as passes.

The inference-context tests actually build GpuModel graphs, initialize
a GPU InferenceContext, evaluate optional nodes and registered subgraphs,
then check numerical results. This proves synthetic ML Drift GPU
graph execution on the PowerVR DXT-48, not just OpenCL availability.

New reusable Android-only test runner:
scripts/probes/run-ml-drift-android-tests.sh.
It rejects x86/Linux binaries before any ADB write, enforces Android
linker, bounds runs with timeout and low process priority, and removes
every temporary test executable. Both official test suites were
repeated with exactly the same 108 passed / 54 skipped and 2 passed,
then device temporary paths confirmed clean. Battery level 100%,
reported battery temperature about 27.2 C after device run.

### Remaining qualification gate

- No app-process JNI / Android linker namespace test yet.
- No real imported ONNX, TFLite, RVC or diffusion model ran.
- No comparison against our LiteRT/ONNX runtime for memory, performance,
  audio quality, thermal load, long-running cancellation or battery.
- Next: isolated app-process initialization then a single
  representative model-specific workload with real weights and
  numerical parity + memory/latency receipts.
- Do not expose an ML Drift backend model option without those gates.

No app installation, installed-model change, data migration,
upstream merge or push was performed.
