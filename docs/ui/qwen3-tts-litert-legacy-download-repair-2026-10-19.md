# Qwen3-TTS legacy LiteRT download repair — 2026-10-19

## Goal and execution boundary

In response to Solidaxel/Qwen3-TTS-GGUF, investigate the existing RikkaHub
Qwen3-TTS path before considering another runtime. The current RikkaHub
Qwen3TtsEngine is a host-orchestrated 3-graph LiteRT CPU implementation
that expects talker_int4.tflite, mtp_fp32.tflite, codec_decoder_fp32.tflite,
flat host tables, demo_speaker.npy, and vocab/merges. The publisher's current
top-level `.litertlm` packages are **not** the same runtime contract.

## Evidence

Source: https://huggingface.co/litert-community/Qwen3-TTS-12Hz-0.6B-Base

Hugging Face repository API on 2026-10-19 confirmed the original files are
now under `legacy/`, pinned at revision
`d6ba751e42044ce443bab046a80a581076d9fc90`.
The original catalog pointed to now-incorrect root paths, and claimed ~1.4 GB
despite requiring approximately **1,880,805,470 bytes** of model files.
The tree exposed exact byte counts and SHA-256 LFS object hashes for the
large files. The two small tokenizer files and the tiny speaker reference
were independently fetched and hashed. A missing final `c` in the speaker
hash was caught by the new test suite, independently rechecked, and fixed.

## Implemented

- `speech/.../qwen3/Qwen3TtsCatalog.kt`: pinned legacy subpaths,
  flattened local names matching the engine, byte counts and exact SHA-256
  for all ten inputs; bundle status requires complete SHA verification,
  not merely presence by filename. Distinguishes legacy 3-graph engine
  from modern LiteRT-LM package.
- `app/.../setting/components/Qwen3TtsDownload.kt`: one Qwen-specific
  verified install path using existing ModelInstall SHA/size-checked atomic
  promotions and resumable `.partial` files, plus available-storage reserve,
  progress, path safety and nonreplacement of unverified existing user files.
- `app/.../setting/components/TTSProviderConfigure.kt`: installed status
  hashes in the IO dispatcher (not the UI thread); download button uses
  pinned catalog, with no unverified arbitrary source treated as trusted.
- `speech/.../provider/providers/Qwen3TtsProvider.kt`: manual model paths
  also require manifest verification before first native engine load
  (not rehashed every utterance when the session is reused).
- Unit tests cover manifest shape, compatibility filenames, corruption
  rejection, simulated HTTP downloads and resume, existing-file reuse,
  nonoverwrite of bad user files, insufficient storage and failure to
  mark corrupt downloads as installed.

## Verification

`./gradlew :speech:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
passed with 100 speech tests across 29 suites, and 1,529 app tests across
223 suites, zero failures/errors/skips. Android arm64 debug APK built.

A new private checksum-verified app backup was created before in-place
`adb install -r`, which returned **Success**. App launch returned a
normal chat screen. The Pixel later disconnected, preventing navigation
to the new provider editor and actual audio testing. No model weights were
downloaded. Existing installed app data and preferences were preserved;
no TTS inference speed, model fidelity, GPU support or cloned voice
behavior has been demonstrated on Pixel. This is a catalog and
installer/packaging repair, not proof that synthesizing audio works.

## GGUF separate branch

Inspected current `ggml-org/llama.cpp` source at revision `1b29621`
in `/tmp/qwen3-tts-llama-source-check` (disposable read-only checkout).
Its `tools/tts/README.md` documents `llama-tts`, Qwen3 TTS language
selection and optional `--tts-speaker-file`. This is a different backend
from both our LiteRT host loop and Solidaxel's hybrid GGUF + ONNX package.
No `llama-tts` binary was initially available on PATH; no full TTS
weights downloaded; no GGUF model inference benchmark executed.
Any integration must demonstrate model loading, reference voice,
audio output, cancellation, latency and memory on an intended host
before advertising it as an Android provider.

## Follow-up gates

- Regain Pixel ADB and visually confirm Qwen3 download section / verified
  installation badge. Do not start the 1.88 GB install without a clear
  need and adequate phone storage.
- For audio runtime acceptance: explicitly download licensed model
  weights, verify total size + checksums, attempt representative speech,
  record real RTF, RAM, errors and cancellation on the device.
- For GGUF separately: compile/test native llama-tts without treating
  a help screen or compilation as proof of speech; only then evaluate
  model-backed audio. Avoid fake integration provider entries.
- Do not push branch wholesale; existing local history has unrelated
  unpublished commits.
