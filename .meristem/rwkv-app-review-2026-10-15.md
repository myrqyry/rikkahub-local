# RWKV App 4.8.5 — selective mechanisms for RikkaHub Local

**Source:** RWKV-APP/RWKV_APP release tag 4.8.5 (October 9, 2026).
The release updates G1k model weights for 1.5B/2.9B/7.2B/13.3B,
adds macOS Core ML weights, and fixes unexpected interruption of
ordinary chat responses.

Read:
- https://github.com/RWKV-APP/RWKV_APP/releases/tag/4.8.5
- https://github.com/RWKV-APP/RWKV_APP/blob/4.8.5/remote/762.json
- https://github.com/RWKV-APP/RWKV_APP/blob/4.8.5/docs/contracts/model_quantization_catalog.md
- https://github.com/MollySophia/rwkv-mobile/blob/master/README_API.md
- https://github.com/RWKV-APP/rwkv_mobile_flutter

## Interesting upstream mechanisms

The catalog explicitly records backend, supported platforms, SoC limits,
quantization, artifact size/digest and capability tags. Build-matched
catalog fallback prevents incompatible remote configuration.
The separate Flutter/native bridge owns execution isolation, model
load/unload, streaming, performance diagnostics and state save/restore.

Published build 762 Android candidates include:
- G1k 1.5B QNN W8 for Snapdragon 8 Gen 3: 1,788,809,216 bytes.
- G1k 1.5B MediaTek NP9 W8 for Dimensity 9500: 1,951,027,200 bytes.
- G1j 1.5B Palm CPU W4: 1,106,001,924 bytes.
- G1j 2.9B Palm CPU W4: 1,951,288,900 bytes.

**G1j and G1k are different models.** The two accelerated G1k
Android artifacts are SoC-restricted and do not qualify for Tensor G5.
The Palm G1j model is a *candidate*, not proven fast or even loadable
on our Pixel. No RWKV weights were downloaded or executed here.

## Existing local capabilities that did not need copying

RikkaHub Local already has pinned catalog SHA-256 and size checks for
verified downloads, execution backend and SoC capability probing,
memory-admission planning, model lifecycle states, local import
format-header checks, and typed model-role selection. Importing
RWKV's complete Flutter UI, second downloader or backend catalog
would duplicate these mechanisms.

## What was actually adapted

### 1. Safe manual model imports — dd9a3e8ee

Prior URI import copied directly into a model's final destination,
possibly truncating a previously installed model. A malformed file
or cancelled stream could then cause cleanup to delete that model.

New ModelFileImport stages a unique file under the same private
storage directory, checks a model-format header, checks coroutine
cancellation while copying, and moves without replacing any existing
target. Applies the no-replacement rule to classification/moving
GGUF into llama.cpp storage too. Tests cover valid LiteRT and GGUF,
duplicate targets, malformed HTML, source failures, cancellation,
late target arrival, symlink targets and closed source streams.
If registration fails after publication, the intact imported file
remains available for inspection rather than being destroyed.

This is *not* publisher provenance verification or a real model-run
test for manually imported files: only trusted catalog entries carry
publisher SHA-256.

### 2. Installed is not runnable — bfb4ec7fe

The model resolver formerly allowed installed=true to override
lifecycle states VERIFYING, INSTALLED, ERROR or INCOMPATIBLE.
It now honors existing model lifecycle readiness for explicit and
automatic local selection, with no cloud fallback under LOCAL_ONLY.
Focused tests cover all these states, cloud fallback under ANY, and
normal READY resolution.

Remaining limitation: SettingsModelRegistry can currently assign READY
to a file simply because its registered path exists. This change
cannot itself establish native load/generation success; a separate
runtime qualification state is a future task.

## Verification and side effects

Commands: Gradle local-llm:testDebugUnitTest, ai:testDebugUnitTest,
app:testDebugUnitTest and app:assembleDebug, offline.
- local-llm: 507 tests, zero failures
- AI: 200 tests, zero failures
- app: 1,488 tests, zero failures
- debug Android APK: built successfully

No installed Pixel app change, model downloads, inference benchmark,
database migration, upstream merge or push.

## Remaining work

Native RWKV is a **separate backend experiment**, not an installed
provider. The G1j Palm CPU model uses .mollm, not LiteRT-LM or GGUF.
Before any Kotlin/JNI adapter or model-picker row, validate native
C ABI/build compatibility and separately applicable license terms
for rwkv-mobile, backend libraries and model weights. The tagged
Flutter app and wrapper declare Apache 2.0; do not infer every
third-party native component and weight uses the same license.

Benchmark Tensor G5 actual model loading, real generated text,
verified token speed, cold/warm model state, RAM/thermals, cancellation,
and model switching. Explore recurrent state caching only after tests
prove state cannot leak between conversations or model versions.
Do not install or surface an inert "RWKV" backend.
