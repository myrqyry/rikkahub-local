# models.dev provider directory (2026-10-28)

## Scope

RikkaHub's **Models → Add → Provider / API → Browse models.dev providers** now reads
the public `https://models.dev/api.json` catalog on demand. It is a source of
provider discovery metadata, **not** a credentials store, inference service,
model availability probe, or replacement for locally configured providers.

The upstream source is [anomalyco/models.dev](https://github.com/anomalyco/models.dev),
MIT licensed. API data is community maintained; entries, capabilities, and pricing
may be stale or inaccurate. Live endpoint HTTP 200 was observed from the
desktop on 2026-10-28 (about 5.9 MB, 233 provider entries).

## Implementation

- `data/modelregistry/ModelsDevProviderDirectory.kt` parses provider IDs,
  names, endpoint URLs, adapter names and indexed model counts.
- The add-provider sheet offers searchable directory browsing alongside its
  existing custom API and already-configured provider options.
- Only catalog records advertising `@ai-sdk/openai-compatible` with a valid
  HTTPS endpoint (or explicit loopback HTTP endpoint) can create an OpenAI-compatible
  provider draft. Others remain visible but cannot be incorrectly added as that
  adapter. The catalog is not a substitute for provider-specific protocol support.
- A draft is **disabled**, with an empty API key and zero imported models.
  The user must review it, configure credentials, enable it and fetch available
  models using the existing provider workflow. No implicit network calls to new
  providers are made merely by browsing the catalog.
- A 12 MB cap protects catalog downloads. The public JSON cache lives in the app
  cache directory and refreshes after 24 hours. A stale cache is displayed
  explicitly when offline; an unavailable uncached catalog does not block
  existing/manual provider setup.
- Locally configured provider entries, IDs, settings, saved credentials, API
  routing, offline/local runtimes, and application storage schemas are untouched.
  Opening the public directory itself requires network access.

## Evidence and limitations

- Live JSON shape and 233 provider count confirmed from yuzu-mint with HTTP 200.
- `ModelsDevProviderDirectoryTest` verifies mapping, adapter gating, endpoint
  validation, disabled draft settings and malformed input.
- `ModelsDevProviderCacheTest` verifies refresh reuse, offline stale fallback
  and failure without cache. A test exposed and fixed clock skew around
  newly-written cache timestamps.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed.
- No physical Android UI, installation, catalog network call from the phone,
  or provider authentication test has been performed. A passing build is not
  proof that every catalog endpoint supports RikkaHub's OpenAI-compatible
  request format.
- Future extension: add individually verified provider-specific adapters and
  model metadata enrichment without conflating public catalog claims with
  actual runtime capabilities.

## 2026-10-28 — Automatic model metadata on import

The existing provider-directory cache is now also used to auto-configure **new**
model selections in Provider Detail → Models, including typing a model ID and
choosing one from the provider's actual `/models` listing. A second, searchable
**Catalog** picker shows advertised models from the matched models.dev provider.
Catalog entries are deliberately distinct from models the provider has confirmed
as available to the user's account, and catalog selection does not authenticate
or imply that the provider will serve that model.

- `data/modelregistry/ModelsDevModelCatalog.kt` maps exact provider+model IDs
  to supported text/image modalities, tool calling, reasoning, structured output,
  temperature support, reasoning effort choices, context/output limits, and
  advertised input/output token prices. Public pricing is USD per million
  tokens; RikkaHub's pricing fields are USD per token.
- Provider identity requires an exact endpoint including path and a compatible
  adapter declaration. Default OpenAI, Claude, and native Gemini endpoints
  have explicit mappings; mismatched endpoints, unverified aliases, and
  loopback servers cannot acquire unrelated catalog capabilities. Unsupported
  audio/video modalities are not presented as implemented text/image support.
- Provider-reported OpenRouter capabilities/pricing win over catalog claims;
  models.dev only fills missing known fields there. Custom and generic
  providers whose API only returns IDs use the catalog when a precise match is
  available, and the previous static registry otherwise.
- `ai/provider/Model.kt` gained optional backward-compatible model metadata:
  max output tokens, temperature support, structured-output support, reasoning
  efforts, and provenance. No data migrations or destructive preference changes.
- AI request builders for OpenAI chat/responses, Claude, and Gemini honor explicit
  temperature support and cap explicit output-token requests to a known
  advertised model limit. For unknown capabilities/limits, legacy request
  behavior is retained. Provider-specific generation behaviors still apply.
- Manually edited or previously saved models are **not overwritten** by
  refreshes. The new-model dialog prepopulates details on recognized model IDs,
  handles a catalog arriving after ID entry, and does not overwrite edits made
  while loading. A successful catalog load is not proof of actual API support.
- The catalog is loaded only on remote-provider model-settings pages, on
  `Dispatchers.IO`, preserving local/cloud execution boundaries and the existing
  24-hour cached/offline fallback. Only the selected provider's models are
  retained in the in-memory catalog view.
- Relevant tests: `ModelsDevModelMetadataTest`,
  `ModelMetadataDefaultsTest`, `OpenRouterModelParseTest` plus existing AI
  and app suites. RED-before-GREEN checks caught endpoint-adapter confusion,
  loopback identity collisions and conflicting temperature metadata.

**Verification:** `./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest
:app:assembleDebug --offline` PASS on Linux Mint. An opt-in live-source
acceptance test `ModelsDevLiveCatalogContractTest` additionally passed with
`RIKKAHUB_MODELS_DEV_LIVE_JSON` pointing to a fresh HTTP 200 JSON snapshot
(5,922,427 bytes), validating actual OpenAI `gpt-4o` and OpenRouter
`openai/gpt-4o` entries and the model capability/limit/pricing mappings.
It skips in normal offline unit test runs unless explicitly enabled.
No physical Android
screen/API-key/response roundtrip was performed for the new feature.
The catalog is community maintained, and model metadata can be stale; never
treat published capabilities as execution receipts.

**Not yet automated:** complex provider-specific parameter contracts (e.g.,
all reasoning token-budget modes), runtime probing of remote capabilities,
specialized audio/video or local model formats, additional LiteLLM reconciliation,
and bulk rewriting of existing user-edited models. These require explicit,
typed adapters/verified behavior rather than guessing.

## 2026-10-28 — Request-side reasoning effort enforcement

Previously, a model could import `reasoning_options` from models.dev but the
request builders ignored them. An assistant configured with XHIGH could still
send `xhigh` to a model explicitly advertising only low/medium/high.

- `Model.supportedReasoningEffort(requested)` resolves adjustable levels against
  the model's advertised `reasoningEfforts` with a deterministic ordered scale
  (minimal < low < medium < high < xhigh/max). It picks the highest supported
  level at or below the requested level. A provider advertising `max` can use
  that in place of xhigh for an XHIGH request. Unknown effort names are not
  guessed. When no safe effort is available, no explicit effort is sent and the
  provider chooses its default; this does **not** guarantee the resulting effort
  is lower than requested. Empty catalog metadata preserves legacy behavior.
- Integrated the resolver into OpenAI Responses, OpenAI-compatible Chat
  Completions (including OpenRouter and OpenCode-compatible hosts),
  Claude's `output_config.effort`, and Gemini 3's `thinkingLevel` mapping.
  Provider-specific budget/toggle semantics are not accidentally treated as the
  same protocol.
- Explicit OFF and AUTO retain existing provider-specific behavior. The current
  adapters cannot guarantee that OFF is available for every model, especially
  providers with a mandatory minimum reasoning effort; don't silently translate
  OFF into an apparently equivalent but reasoning-enabled mode.
- This changes **request generation** for models with known effort metadata; it
  does not modify persisted assistant preferences, override the model selected
  by a user, fetch new data, or modify provider credentials.
- Tests: `ai/src/test/.../ModelReasoningEffortTest.kt` verifies pure selection,
  and `ProviderReasoningEffortRequestTest.kt` verifies serialized OpenAI
  Responses, OpenRouter, OpenAI Chat, and Claude requests. Two Gemini 3 request
  tests (in `GoogleProviderMessageTest.kt`) first reproduced unsupported
  medium/low parameter emission, then passed with the fix.
- Full local verification:
  `./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline`
  PASS, with `git diff --check` clean. No ADB phone was attached and no real
  authenticated provider request was made, so service behavior remains unverified.

## 2026-10-28 — Composer thinking controls match the current model

Previously, the composer hid reasoning only when the REASONING ability was
absent, and the picker always displayed every ReasoningLevel. The setting shown
could disagree with the request sent after switching models.

- A pure provider-aware policy (ComposerReasoningOptions.kt) now reads the
  selected model's known reasoning options: named effort values, toggle, or
  budget_tokens. Only advertised effort levels are offered; AUTO uses provider
  defaults. The catalog's max maps to XHIGH, but minimal is not mislabeled
  as LOW. No reasoning support means no button. One effective choice also
  means no composer button.
- OFF requires both a model-advertised disable mechanism (none or toggle)
  and a known provider adapter that can express disabled reasoning. Gemini 3
  MINIMAL does not count as OFF. Unknown OpenAI-compatible hosts do not show
  OFF just because the catalog lists a toggle.
- Budget-token-only entries remain AUTO-only rather than offering invented
  effort settings until we implement a proven numeric control for each adapter.
  A catalog model with no control contract also offers AUTO only. Legacy
  models without catalog metadata retain their existing choices.
- Switching models resets an open picker and derives an effective level
  without rewriting the saved assistant preference. GenerationHandler uses
  the same effective level for the actual request, preventing a stale XHIGH
  or OFF selection from contradicting the composer.
- OpenAI Chat models explicitly advertising none now send a real
  reasoning_effort=none for OFF rather than the former LOW fallback.
- Test-first pure policy tests, catalog parser tests, and serialized-request
  tests passed. The opt-in live-data contract also passed against real
  models.dev JSON for GPT-5.5 and Gemini 3.5 Flash; those entries expose
  different picker options as expected.
- Full AI JVM suite, app JVM suite, and debug Android APK build passed
  offline. No ADB-connected phone or authenticated provider requests were
  available; rendered UI behavior and live provider support remain untested.

## 2026-10-28 — Advertised audio/video/PDF metadata and complete Gemini model listing

Two further improvements, implemented without broadening the app's inferred runtime
capabilities or modifying user settings:

- Models.dev supports `modalities.input` and `modalities.output` values
  `text`, `image`, `audio`, `video`, and `pdf`. Imported models now retain
  the catalog values in optional `advertisedInputModalities` and
  `advertisedOutputModalities` metadata. The existing `inputModalities`
  and `outputModalities` fields continue to describe what the currently
  integrated app adapter can pass through safely (currently text/image).
  PDF is a document format, not a new general inference transport. The model
  details screen explicitly distinguishes advertised media from a verified
  working adapter. Existing serialized models deserialize unchanged.
- Catalog audio-only models are *not* added to the chat/image suggestion picker
  merely because their audio capability is known; only compatible chat/image
  models are suggested. The metadata supports future modality-specific catalogs,
  attachment routing, and adapter verification without lying about availability.
- The Google provider models.list request previously fetched just one page of
  100 entries even when the response included `nextPageToken`. It now follows
  all pages, preserves exact page-token encoding, avoids repeated IDs, and
  rejects repeated tokens or more than 50 pages. A later-page error fails
  the listing rather than returning an incomplete subset as if it were complete.
  Embed-only entries remain identifiable as embedding models.
- Google officially documents `pageToken` / `nextPageToken` for its Models API:
  https://ai.google.dev/api/models
- `GoogleModelPaginationTest` exercises the actual provider HTTP request path
  with a synthetic OkHttp interceptor: multiple pages, model type, URL encoding,
  duplicate IDs, repeated-token guard, and failure on later page.
  `ModelsDevMediaMetadataTest` covers catalog round-trip, safe adapter
  modalities, import precedence, and backward-compatible serialization.
- A real models.dev JSON acceptance test checks `gemini-3.5-flash` as
  advertising text/image/video/audio/PDF input while the current RikkaHub
  adapter retains only its integrated text/image input flags.

### EmbeddingGemma 2 boundary

Google's released EmbeddingGemma 2 full model is a 740M-parameter, unified
text/code, image/visual-document, audio, and video embedding model with a
768-dimensional output vector and an 8192-token shared context. The optional
vision/audio encoder modules are *not* present in the text-only 270M LiteRT
bundle currently pinned by this Android app.

The actual installed model path remains
`local:embeddinggemma-2-text-270m`, revision
`9be6e8b90982095dc05c2bd162e4b954ee4dbac7`, with an explicitly
text-scoped embedding space. The runtime invokes
`EmbeddingEngine.computeEmbedding(InputData.Text(...))` only. It must not be
advertised as multimodal embedding inference yet. Future work needs a real
multimodal LiteRT model/runtime adapter and media encoders, separate vector
index compatibility, and an actual device inference test.

Sources:
- https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2
- https://developers.googleblog.com/embeddinggemma-2-the-developer-guide/
- https://github.com/anomalyco/models.dev

## 2026-10-28 — Resolve catalog before committing new model imports

The authenticated provider model list and models.dev catalog fetch are concurrent.
Previously the provider picker was usable as soon as /models returned, even
when models.dev was still downloading; pressing Add at that moment saved a
model with static, potentially incorrect guessed capabilities. We deliberately
do not modify saved user models later, so this timing bug became permanent.

- New app-level `ModelCatalogLoad` has Loading and Ready(catalog?) states.
  Ready(null) means the catalog attempt finished without metadata; it permits
  manual/provider model imports with the previous static registry fallback.
  Loading does *not* persist a fallback model merely because metadata has not
  arrived yet. Provider-model selection is disabled while the catalog is
  pending, with a small progress indicator; manual model entry can begin but
  its Add confirmation also waits for readiness. There is no unbounded wait:
  public catalog fetching uses the existing finite 20-second timeout.
- `ModelCatalogLoad.resolveImport` is the deterministic import path used for
  individual and bulk selections, preserving rich provider-native metadata
  (especially OpenRouter) rather than blindly applying static capabilities.
- A `ProviderScopedCatalogLoad` identifies its provider ID and endpoint.
  Compose's produceState can otherwise retain a previously completed value
  briefly across a key change; a mismatched result is treated as Loading,
  never as a usable catalog for a newly selected provider. The actual
  authenticated provider `/models` list uses `ProviderScopedModelList` keyed
  by the complete provider configuration as well, preventing old API-key or
  previous-provider results from appearing in another provider's picker.
- The manual model dialog retains its prefill-on-ID behavior and the
  existing edited-field check; its confirmation does not independently apply
  metadata again over a manual override.
- Focused JVM regression tests: `ModelImportResolutionTest` exercises Pending,
  Ready with exact catalog, unavailable catalog fallback, rich provider
  precedence, unrelated host refusal, and stale provider result rejection.
  Android JVM tests and debug build passed. UI interaction on a physical
  Android device remains unverified; no installed data or credentials changed.

### Verified LiteRT-LM multimodal upgrade path (2026-10-28)

Important correction: the current app and local-llm Gradle dependencies **already
pin LiteRT-LM 0.18.0**. An older EmbeddingGemma2Backend comment still said
0.17.0 and was updated to avoid version drift. Google's LiteRT-LM v0.18.0
release notes explicitly describe multimodal EmbeddingGemma 2 in Kotlin.
Version 0.18.1 documents an ABI fix for builds from source; don't assume it
is required for the current prebuilt Android artifact.

Official packaged model choices:
- Text-only 270M (currently installed code path): ~165 MB.
  https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm
- Text + vision 440M: ~388 MB.
  https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm
- Full text + vision + audio 740M: ~485 MB, with video through the vision
  encoder's frame processing.
  https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm

Sources:
- https://developers.google.com/edge/litert/releases/litert_oct
- https://developers.google.com/edge/litert-lm

**Next actual inference milestone:** inspect the pinned Android
`EmbeddingEngine`/InputData 0.18.0 API for vision/audio input; add a separately
pinned model artifact, hashes and opt-in download without overwriting the
existing 270M checkpoint. Verify one genuine image embedding on device,
then PDF-page/image retrieval, then audio embeddings. Keep a separately scoped
index/embeddingSpaceId until vector compatibility has been tested. The
presence of a supported runtime version and publicly packaged files is
*not* proof that this app can yet embed media on its phone.

## 2026-10-28 — Make models.dev the primary Provider APIs picker

The Models → Add → Provider APIs UI previously presented the entire saved
built-in `DEFAULT_PROVIDERS` list first, with the public models.dev directory
hidden behind an extra "Browse" row. A user-supplied Android screenshot showed
the consequence: a long, static list dominated the sheet instead of the
searchable live provider catalog.

**New presentation:** Provider APIs opens directly into the cached models.dev
directory, with a compact search box and two sections inside one vertical
lazy list:
- **Popular:** OpenAI, Anthropic, Google/Gemini, OpenRouter, DeepSeek, xAI,
  Groq, Mistral, Cerebras, in a small curated order;
- **All providers:** the remaining entries alphabetically, without duplicates.

This is presentation prioritization, not another hardcoded provider registry.
The public catalog's entries, labels, indexed-model counts, and cache/stale
handling remain the source of all searchable directory rows. The same shared
on-device cache is used for offline browsing; a catalog load failure still
offers manual setup and a route to saved providers.

**Real adapter/credential preservation:** The selection resolver looks for an
existing provider configuration first, including native Google and Claude
adapters and the built-in OpenAI, OpenRouter, DeepSeek and xAI settings. Clicking
a matched entry opens its actual settings page, preserving ID, API key, model
assignments, saved options, enabled state, and any custom edits. Never
duplicate an existing API configuration merely because the display names in
models.dev differ (e.g. Google vs Gemini, Anthropic vs Claude).

For an absent supported provider, explicitly selecting its catalog entry
opens a new disabled, empty-key draft; it does **not** enable, contact or
auto-configure a network service. Native OpenAI, Google and Anthropic use
their actual provider adapters; catalog-marked verified OpenAI-compatible
endpoints use the generic OpenAI adapter.

Additional, **independently documented** OpenAI-compatible Chat Completions
endpoints are supported for the pinned providers whose models.dev npm entry
names a native adapter but omits an API URL:
- Groq `https://api.groq.com/openai/v1`
  https://console.groq.com/docs/openai
- Mistral `https://api.mistral.ai/v1`
  https://docs.mistral.ai/resources/migration-guides
- Cerebras `https://api.cerebras.ai/v1`
  https://inference-docs.cerebras.ai/resources/openai

These are **basic chat compatibility claims**, not verified equivalence to
each provider's SDK, native feature set, model-list permissions or exact
reasoning/tool parameters. Unknown catalog providers without an adapter or
safe compatible endpoint remain discoverable but visibly unavailable rather
than emitting untested requests.

The previous built-in list was **not deleted from settings or migrated**.
Instead a secondary **Other saved API setups** route preserves access to every
existing/default/custom remote entry, including legacy services absent from
models.dev. A separate **Custom API endpoint** action remains available.
The main UI no longer forces a long list of default-disabled provider templates
to appear ahead of the actual catalog.

**TDD and validation:** `ModelsDevProviderPresentationTest` covers pinned
ordering, searching, unique listings, existing Google/DeepSeek/OpenRouter/xAI
configuration reuse, draft safety, verified native-style endpoints, custom
URL matching, and unsupported adapters. An opt-in acceptance test using the
real downloaded models.dev catalog checks all public rows are accounted for
and every pinned provider is selectable. Android debug APK build and focused
tests passed. Final full regression result is tracked in `ACTIVE_WORK.md`.
Physical Android UI screenshot/interaction of the new build remains
unverified until it is installed on a device; phone data was untouched.
