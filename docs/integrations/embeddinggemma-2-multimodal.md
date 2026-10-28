# EmbeddingGemma 2 — on-device multimodal RAG

Implemented on 2026-10-28, local branch `aster/device-execution-profile`.

## Scope and present verification

RikkaHub has an actual Android LiteRT-LM 0.18.0 `EmbeddingEngine` implementation
for three revision-pinned EmbeddingGemma 2 bundles. These are *embedding*
models, not chat/generation models. The three variants are independent,
user-selected downloads; existing text-only RAG selection, files, and
index-space ID remain compatible with the previous implementation.

| Variant | Bundle / pinned commit | Exact size | SHA-256 |
| --- | --- | ---: | --- |
| 270M TEXT | `litert-community/embeddinggemma-2-text-270m-litert-lm` / `9be6e8b90982095dc05c2bd162e4b954ee4dbac7` | 164626432 B | `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb` |
| 440M VISION | `litert-community/embeddinggemma-2-text-vision-440m-litert-lm` / `e301f74d5551b0c2641bd5cb4652a76239d5c5f8` | 387710976 B | `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0` |
| 740M FULL | `litert-community/embeddinggemma-2-740m-litert-lm` / `24d962e906c7d332c6428e71c9676855024569e2` | 484622336 B | `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c` |

Official model revisions, filename sizes, and LFS SHA-256 were checked
against the public Hugging Face repository API. Each file is downloaded into
the app's own `local-models/embeddinggemma2/` directory under its *different*
filename, with a 256 MiB free-space reserve and checksum verified *before*
the downloader's atomic promotion. Existing files are never overwritten.
Selection requires a valid SHA; removing the active variant is blocked.

The exact pinned Android AAR was inspected locally: 0.18.0 includes
`EmbeddingEngineConfig.visionBackend`, `audioBackend`,
`InputData.Image(ByteArray)`, `InputData.Audio(ByteArray)`, and
`EmbeddingEngine.computeEmbedding(List<InputData>, EmbeddingOptions)`.
The implementation makes actual calls through this native engine with
`InputData.Text`, `Image`, and `Audio`, using CPU adapters, and checks
that 768-dimensional results are finite. It does not use a mock embedding
implementation or fall back to remote APIs.

**Verification boundary:** desktop Gradle JVM tests and debug build are
*not* proof that the 440M/740M native model actually initializes or yields
correct image/audio vectors on a physical Android device. No phone is attached,
no multimodal model was downloaded to the nearly-full development machine,
and no actual image/audio inference outputs have been measured. Keep the
feature explicitly testable rather than claiming runtime acceptance.

## User path

1. Open **Models → LiteRT models** and download the desired EmbeddingGemma 2
   variant. Once verified, explicitly **Use ... for RAG**.
2. Open **Settings → RAG → Local multimodal retrieval**. A 440M/740M variant
   exposes **Index image, video, PDF, or WAV**. A 440M model accepts JPEG, PNG,
   MP4, or PDF; 740M additionally accepts RIFF/WAVE audio.
3. Choose an Android Storage Access Framework source. The original media is
   read locally only. The app stores normalized vectors and metadata including
   source label, content URI, page number or video timestamp. It never uploads
   the source for this embedding operation.
4. Search the indexed visual/audio media by typing a text query. Results are
   ranked by cosine similarity of real model embeddings in the same pinned
   variant's index space. Media is filtered before ranking/limiting so text
   document chunks cannot crowd out media results.

PDF: Android `PdfRenderer` rasterizes 1–24 pages to bounded PNG images
(max 1024 px on the longest dimension), then embeds *each page as an image*.
It does not assume a PDF byte payload is accepted directly by LiteRT.
Large files over 64 MiB (when stat size is known) or PDFs with more than 24
pages produce a clear error.

Video: `MediaMetadataRetriever` extracts up to 12 evenly sampled MP4
frames from a video up to 30 minutes, compresses them to bounded JPEGs, and
embeds them as separate vision inputs with timestamps. This is **frame-based
visual retrieval**, not native temporal audio/video embedding, and does not
index a video's soundtrack. GIFs/HEVC/MKV are not claimed supported.

Audio: the 740M model receives normalized **16 kHz mono PCM16 RIFF/WAVE**
bytes via `InputData.Audio`, matching Google's stated audio sample-rate/channel
requirements. The local normalizer strictly validates RIFF and WAV chunks,
accepts mono/stereo 16-bit uncompressed PCM at 8–96 kHz, downmixes stereo,
and resamples on-device (averaged interval downsampling; interpolated
upsampling). Duration is capped at five minutes; files exceeding 64 MiB,
malformed headers, partial frames, floats, compressed WAV, and MP3 are rejected
with explicit errors. The first resampler is a bounded baseline, not a
studio-grade DSP implementation; native inference quality remains unverified.
No transcript or fake text embedding substitutes for audio.

**2026-10-28 HF follow-up:** LiteRT package listings for the 270M/440M/740M
bundles still show October 6 as their latest updates; Google's principal
EmbeddingGemma 2 model repository shows October 28 activity. These are
separate repositories and do not establish changed Android LiteRT weights.
The normalizer fills a real input-contract gap discovered while reviewing
new model documentation. RED-first tests cover 48 kHz stereo conversion,
exact 16 kHz PCM samples, odd RIFF metadata chunks, malformed headers,
unsupported encodings, and 8→16 kHz conversion. The complete AI JVM suite,
app JVM suite, and debug APK build passed offline on Linux Mint. Physical
Android 740M audio inference is still untested.

Reference: https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2

No data store migration or cloud service required. A separate
`embeddingSpaceId` for each variant prevents querying across incompatible
spaces, even though all three output 768 floats. Merely switching models
does not delete old vectors; reindexing source documents may be necessary
for the new space. Media IDs include model-space and source URI hashes to
avoid overwriting media vectors from another variant.

## Safety and limitations

- No app installation, user-data deletion, database reset, settings override,
  automatic model download, or cloud substitution was performed.
- Current interface displays media search labels and similarity, not
  thumbnail previews or opening the original file. Retaining the original
  content URI in metadata does not guarantee a persisted document-provider
  grant across application restarts.
- The text-only embedder and Qwen path still function through the old
  interfaces; other embedders are never incorrectly claimed to embed media.
- Failed native loads return `null`, with errors logged locally; media
  ingestion/search surfaces failures rather than presenting simulated vectors.
- PDF and video ingestion is bounded; a failed multi-page/frame import can
  retain already-indexed earlier pages/frames. Future resumable/atomic group
  import is separate work.
- Search results may be semantically poor until validated with physical-device
  media retrieval tests. Embedding dimensions, model metadata, compiled Kotlin,
  and unit tests alone do not establish actual multimodal model behavior.
- The current RAG text ingestion still uses its own document chunk IDs.
  Reindexing the *same textual document ID* under another space can replace
  old rows; selection itself does not. Do not promise indefinite coexistence
  for same-ID text reindexes until fixed with a scoped primary key.

The RAG index status now counts valid rows for the selected space without
marking them stale just because other model-space vectors coexist. If the
selected space is absent but old vectors exist, it still indicates that the
selected model needs indexing. A regression test covers both spaces coexisting.

## Test coverage

- `EmbeddingGemma2VariantsTest`: immutable asset hashes and sizes, old
  text compatibility, selection resolution, independent model/index spaces,
  corrupted-file refusal.
- `EmbeddingGemma2MediaInputTest`: native image/audio input construction,
  format and byte bounds, variant capability checks.
- `MediaEmbeddingRepositoryTest`: actual embedding method dispatch,
  media-only queries, vector-space isolation, change-of-model protection,
  high-volume text-vs-media search regression.
- `MediaRagImportRulesTest`: MIME routing, stable opaque IDs, PDF limits,
  bounded video frame scheduling, model capability dispatch.
- Existing RAG index, provider, and model-manager JVM tests.

## Source references

- https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2
- https://developers.googleblog.com/embeddinggemma-2-the-developer-guide/
- https://ai.google.dev/edge/litert/litert_lm/embedding_models
- https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm
- https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm

## 2026-10-28 — Pre-device Compose layout and model-selection corrections

A targeted source audit of the phone-facing LiteRT gallery and RAG settings
uncovered several issues before device acceptance:

- The LiteRT gallery's entire verified-package section was an unbounded,
  non-scrolling Compose Column inside the add-model bottom sheet, with the
  general gallery nested below multiple large package controls. The full
  section now has a bounded, vertical scroll viewport to keep package actions
  reachable on shorter devices; model action labels are shorter. The nested
  gallery list remains independently bounded and scrollable. **Only Kotlin
  compilation and tests have been performed; real device layout remains to
  be observed.**
- A previously selected EmbeddingGemma variant could show \"Selected\" even
  when its backing file was missing or only partially present. A tested,
  explicit 6-state display policy now distinguishes selected/available,
  selected/missing, selected/incomplete, unselected/available, unselected/
  missing, and unselected/incomplete. The missing-file state permits a new
  verified download; the incomplete state gives clear recovery directions.
  The UI's expected-size check is not a checksum; selecting a bundle still
  requires full SHA-256 verification.
- RAG media query/results/status are now scoped to the selected model ID, so
  old model search results and completion messages are not presented as new
  model results after a switch (including delayed callbacks). The 440M
  picker button says \"Index image, video, or PDF\" and reserves WAV for the
  740M model with its audio encoder.
- RED-first tests cover the display-state combinations and format-specific
  import labels; full AI/app JVM suites, debug APK build and Android lint
  are run in the preflight workflow. No physical device or emulator was
  attached; none of this is a claim of on-device visual/inference acceptance.

### Device smoke steps once available

1. Open **Models → Add → LiteRT-Models gallery**. Confirm all three
   EmbeddingGemma variants, download/use actions and the lower community
   model gallery can be reached by scrolling on the actual phone.
2. Select a **verified** 440M or 740M bundle (download only with sufficient
   free space). Confirm that the state changes to selected, and
   **Return to default** remains accessible. Don't deliberately corrupt
   installed model files just to test recovery.
3. In **Settings → RAG**, use the format-appropriate file picker and index
   one small local JPEG/PNG or PDF. Search by a descriptive text query.
4. Switch to a different embedding variant. Confirm the old search results
   are no longer displayed, and the selected model's index status is scoped
   correctly. Audio requires 740M and a genuine PCM WAV.
5. Record logcat around any native load/generation errors and compare actual
   retrieval relevance; Gradle unit tests cannot establish native behavior.
