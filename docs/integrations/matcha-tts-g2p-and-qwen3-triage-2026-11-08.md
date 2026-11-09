# Matcha TTS on-device repair; Qwen3-TTS speed triage — 2026-11-08

Repository: `rikkahub-local`. Baseline: clean `aster/mediapipe-parakeet-asr` at `73fd3224b` (10 local commits ahead of origin). No unrelated changes present.

## User-observed problems

- Matcha TTS apparently produces no audible audio in RikkaHub.
- The existing Qwen3-TTS seems to take excessively long to render, prompting interest in newer TTS models from lil-local.

These are separate issues. Only Matcha was modified in this slice.

## Matcha root cause and fix

- Existing on-device bundle in app-private `files/local-models/tts-matcha-tts` contains 4 published `.tflite` graphs plus dictionary/embeddings. It is real, not a missing model.
- RED physical device: `MatchaTtsWaveformDeviceTest`, synthesizing `Hello there. This is a test of the local Matcha speech engine.`, failed with `IllegalArgumentException: Matcha TTS text is too long` in `MatchaTtsPreprocessor.encode` *before any audio was produced*.
- Evidence: bundled dictionary contains 274,927 words and maps most of the test words to IPA, but `matcha` is out of vocabulary and enters the neural G2P fallback. `g2p_meta.json` specifies `char_repeats=3`, 64 phoneme symbols and 96 graph frames.
- Local `MatchaTtsG2p.infer` had incorrectly sent each input character once and *triplicated output phonemes*. DeepPhonemizer uses input expansion (`[lang] + each char repeated 3× + [end]`) and **CTC greedy output** (frame argmax, collapse adjacent repeats, discard blank and controls). Corrected with pure, tested `MatchaG2pCodec`, and closed per-run neural G2P tensor buffers.
- GREEN physical device, initial fix: 1 chunk, 94,976 samples at 22,050 Hz (4.31 seconds); 6.02s wall, peak 0.480, RMS 0.0626, no reported invalid PCM samples. *Non-silent PCM is proven; audible speaker playback was not independently recorded or heard.*
- The existing outer `TextChunker` makes up to 160-character chat chunks, which can still exceed Matcha's **256 text-embedding positions / 512 mel frames**. Added conservative 48-character word-boundary `splitMatchaText`, including explicit handling for very long tokens. Matcha's provider now emits multiple PCM16 segments with exactly one final marker and no whole-utterance buffering. When no postprocessing voice effect is selected, the existing `AudioPlayer.playPcmStream` path begins playback on the first produced segment. Effectful playback still uses the full-buffer processing path. This path is the same underlying PCM streaming infrastructure Pocket TTS already uses.
- Repeated synthesis creates model input/output buffers. `MatchaTtsEngine` now closes buffers for text encoder, decoder and vocoder even on failure, so multiple segments don't accumulate native tensor allocations.

**Final physical device acceptance:** Installed debug APK/test APK in place after checksum-verified DB/shared-prefs/DataStore backup `~/.local/share/meristem/device-backups/2026-11-08-matcha-g2p-preflight` (private, not in Git). Final `MatchaTtsWaveformDeviceTest` passed **OK (1 test)**, generating **2 consecutive chunks** (first nonfinal, last final), **100,608 samples / 4.56 s** of 22.05 kHz PCM, peak **0.6025**, RMS **0.06646**, **7.486 seconds** measured wall. Device instrumentation does not assert human-perceived sound from the speaker. User-selected provider, downloaded models and release app were not modified.

**Regression tests:** RED-first `MatchaG2pCodecTest`, `MatchaTextSegmentsTest`, `TtsStreamingSelectionTest`; green after implementation. Full `:speech:testDebugUnitTest :ai:testDebugUnitTest :app:testDebugUnitTest :local-llm:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain --max-workers=2` passed (359 tasks).

## Qwen3 performance: evidence, not yet a fix

- RikkaHub currently invokes `Qwen3TtsEngine` via the **legacy** host-side CPU `CompiledModel` trio (`talker_int4.tflite`, `mtp_fp32.tflite`, `codec_decoder_fp32.tflite`). It runs the MTP inner model **16× per generated audio frame**, caps generation at 512 frames, then decodes/returns audio **only after the entire sequence**. As deployed it is inherently high latency.
- User's Pixel has the legacy model bundle installed under app-private `files/local-models/tts-qwen3-tts`; verified files exist. No performance measurement of this RikkaHub implementation was made during this slice.
- Publisher `litert-community/Qwen3-TTS-12Hz-0.6B-Base` now recommends optimized `qwen3_tts_int8_v2.litertlm` (~2.44 GB); it requires LiteRT-LM's per-head MTP runtime, **not** the current standalone host loop. On a Pixel 10 Pro, the publisher reports CPU **21.3 s synth / 6.64 s English speech** (RTF 3.21) with v2; GPU **32.4 s / 5.96 s** (RTF 5.43). These are upstream measurements, not RikkaHub results. This remains slower than realtime even when optimized, so it must not be advertised as instant.
- Next honest integration path: check pinned Android LiteRT-LM runtime support for the v2 audio outputs/per-head drafter, add a distinct verified download/runtime contract, and run a **bounded** real-device CPU-first comparison. Do not silently repoint existing users' 1.88 GB legacy installation, download a new multi-GB bundle, or claim generic `.litertlm` chat inference equals working TTS.

Upstream context:
- `https://huggingface.co/litert-community/Matcha-TTS`
- `https://github.com/john-rocky/LiteRT-Models/blob/main/docs/LITERT_CONVERSION_GUIDE.md` (CTC input/output contract)
- `https://huggingface.co/litert-community/Qwen3-TTS-12Hz-0.6B-Base`

## Follow-up: real-use acceptance

Mat later confirmed **“matcha works”** in RikkaHub. This supplies the user-side practical playback confirmation missing at the time of the device waveform test. It does not establish a formal audio-capture measurement or cover every voice-output route. No further Matcha work is indicated by this report; Qwen3 remains deferred.

## Remaining unverified

- Independent capture of speaker/headphone output, routing-specific behavior, and detailed subjective quality/intelligibility assessment. The user did confirm working playback in RikkaHub; don't portray that practical acceptance as still missing.
- Several sequential long paragraphs, cancellation while a native graph is running, and error handling for overflow despite conservative chunking.
- Qwen3 v2 Android TTS engine compatibility/performance in *this* pinned RikkaHub app (not implemented).
