# EmbeddingGemma 2 — physical Pixel 10 Pro acceptance (2026-10-28)

**Status:** Verified native LiteRT text, vision, audio inference and isolated
PDF-to-vision-vector retrieval on the user's attached Pixel 10 Pro (API 37,
arm64-v8a). **Not yet verified:** user-file SAF import from the RAG settings
screen, live database persistence across restarts, real-world search relevance,
multi-page PDFs, MP4 video frame search, audio retrieval ranking, or a 740M
user-selected RAG import through the actual settings UI.

## Device/app safeguards

- Current branch: `aster/device-execution-profile`.
- Debug APK `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` installed
  *in-place* via `adb install -r` to `excp.rikkahub.local.debug`, matching the
  existing Android Debug signer and versionCode 171. The separately installed
  `me.rerere.rikkahub` release app was untouched.
- Prior debug app: installed August 16, last updated October 19; approximately
  7.3 GB of installed local-model files. No data clear, uninstall, DB reset,
  user file deletion or cloud inference.
- Before app update, verified a local private 797 KiB `tar` of the debug
  app's `databases`, `shared_prefs`, and `files/datastore`; located under
  `~/.local/share/meristem/device-backups/2026-10-28-pixel10pro-preflight/`
  on yuzu-mint (restricted permissions). The 7.3 GB model directory was
  **not** backed up or rewritten. The database and settings files were still
  present after the update.
- Existing 270M local bundle (`164626432` bytes) was already present.
  Downloaded the official revision-pinned 440M and 740M bundles using the
  app's own downloader. Independently validated both device-side SHA-256
  checksums against `EmbeddingGemma2Variant` pins.
- At last check Android reported ~1.4 GiB free (`/data`, 99% used),
  **down from ~2.9 GiB before APK update and model downloads**. Do not
  download other large models or silently delete these without the user
  choosing. The 440M/740M downloaded models remain installed.
- Temporarily selected 740M in the LiteRT model catalog to verify the
  `Selected for local RAG` display and `Return to default RAG embedding
  choice` control. Explicitly returned to the original default and confirmed
  `Use 740M for RAG` was displayed again. No permanent preference change.
- A `Support the Developer` prompt appeared while navigating Settings,
  temporarily obstructing scripted navigation; this was not an inference
  failure. A connection/session timeout interrupted screen navigation, but
  the ADB device and the test artifacts survived.

## Device tests executed and **passed**

All instrumentation tests ran against the **actual signed APK/native engine**
and on-device downloaded model weights. The test harness
`app/src/androidTest/...` was rebuilt and installed without reinstalling or
clearing the target application's data. Test input was generated, not read
from the user's documents/photos/recordings.

1. `EmbeddingGemma2DeviceTest#modelFileMatchesOfficialSha256`:
   official pinned 270M file check. Passed.
2. `#producesRealFiniteNormalizedTextVectors`: 270M LiteRT CPU
   query and two document embeddings; finite and L2-normalized 768-float
   vectors. The cat-related passage ranked above the unrelated
   copper/resistivity passage. Passed.
3. `#producesRealFiniteVisionEmbeddingsFromPng`: native 440M LiteRT
   vision embeddings from separate generated red/blue PNGs plus a text
   query, finite normalized 768-float vectors. The two images produced
   different vectors. Passed. **Does not prove color-query ranking.**
4. `#producesRealFiniteAudioEmbeddingsFromWav`: native 740M LiteRT
   audio embeddings from 440 Hz and 880 Hz synthetic, one-second,
   48 kHz stereo PCM WAV files, normalized locally to 16 kHz mono WAV,
   plus an embedded text query. Finite normalized 768-float vectors,
   differing for the two tones. Passed. **Does not prove audio-query
   semantic ranking.**
5. `EmbeddingGemma2PdfDeviceTest#rendersActualPdfAndFindsItsPageThroughNativeVisionSearch`:
   created a disposable one-page PDF in private test cache, rendered via
   Android PdfRenderer to PNG, indexed with the *actual* 440M vision
   encoder into an **in-memory test DAO**, and found that sole indexed
   PDF page by a text query through `EmbeddingRepository.searchMedia`.
   Temporary PDF deleted. Passed. **Does not exercise
   `MediaRagIngestion.ingest` or SAF file-picker UI; one item in the
   in-memory index also does not establish search-ranking quality.**
6. After the individual successes, the entire
   `EmbeddingGemma2DeviceTest` instrumentation class passed:
   **OK (6 tests)**, 9.457 seconds reported by Android's runner. This
   exercised actual model switching among 270M, 440M, and 740M within
   the same instrumentation run, plus selection routing and task-prefix
   contract checks.

## Visual checks (actual device screenshots / UI hierarchy)

- Debug app launched with no observed startup crash; on-device chat
  composer and thinking picker were rendered and accessible.
- Settings → Models → Add to Models → LiteRT-Models gallery displayed
  PP-OCRv5, 270M, 440M, 740M, plus 102 community models.
- Confirmed 440M/740M controls reachable via vertical scrolling on the
  Pixel's 1080×2410 actual display size. Previously long descriptions are
  still text-heavy but did not block control access.
- Settings → Knowledge & Tools → Knowledge & RAG rendered
  a Local multimodal retrieval section, correctly describing the need
  for an explicitly selected 440M/740M bundle before offering media import.
- Original RAG settings displayed no selected multimodal model. After
  the temporary 740M selection, the correct selected state and explicit
  return-to-default action were displayed. Reverted and visually
  confirmed the unselected state.
- Private screenshots and test outputs are stored on the Linux PC in
  `~/.local/share/meristem/device-smoke/2026-10-28-pixel10pro/`.
  These images contain personal app UI; keep them **out of git**.

## Bug discovered and repaired

Existing Android instrumentation tests failed to compile because
`RagEmbeddingSource.LocalEmbeddingGemma2` became a variant-carrying
data class instead of the old singleton. Updated the test to compare
against `RagEmbeddingSource.LocalEmbeddingGemma2(EmbeddingGemma2Variant.TEXT)`.
Then added bounded, disposable synthetic native vision, audio and PDF
acceptance tests. No production implementation changed by these test
fixes.

## Pending steps and boundaries

- Verify the actual `MediaRagIngestion.ingest` path from an Android SAF
  URI and persistent database (with an isolated test provider / fresh
  index, not the user's actual RAG vectors).
- Examine cross-modal **relevance** with multiple competing indexed
  media inputs, rather than only non-identical vectors or one PDF page.
- Check optional MP4 frame extraction and app navigation on a real
  user-like file while maintaining a strict no-user-file-change boundary.
- Allow the user to decide whether to retain both downloaded 440M and
  740M bundles: device free space is very low and both have been
  validated.
