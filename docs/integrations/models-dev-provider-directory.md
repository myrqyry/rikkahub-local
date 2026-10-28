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
