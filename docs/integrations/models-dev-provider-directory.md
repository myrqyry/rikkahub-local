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
