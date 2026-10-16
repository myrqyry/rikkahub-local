# OpenCode 2 preparation for RikkaHub and Meristem — 2026-10-15

Sources checked:
- https://dev.opencode.ai/v2/docs/migrate-v1/
- https://opencode.ai/v2/docs/providers
- https://dev.opencode.ai/v2/docs/api
- Local checkout: ~/loam/opencode-upstream, branch meristem-desktop
- Previous generated snapshot: opencode-api-v2-experimental.generated.md,
  generated 2026-09-20, **not authoritative for today's contract**.

**No V2 migration, V2 installation, service restart, session-database
conversion, or OpenCode source changes made.** The user's OpenCode
desktop worktree has unrelated UI changes and remains untouched.

## What is genuinely breaking

1. Native V2 server routes use /api/* and response envelopes; V1
   integrations calling /project, /session, /session/:id/message
   need a distinct V2 adapter and version-specific regression tests.
   Current V2 POST /api/session/:id/prompt durably admits input and
   schedules execution. It must NOT be assumed to return the final
   assistant text. Track admission, durable events/continuation,
   permissions, rejection and cancellation separately.
2. V1 plugins cannot be ported by simply renaming the config
   plugin key. Their hooks, tools, exports and events need review.
   The current personal V1 config contains 9 plugin entries.
   Preserve existing plugin ownership/selection until individually
   verified under a real V2 server.
3. V2 moves terminal client prefs to a global cli.json, changes
   permission syntax/actions, and V2 currently **allows
   external_directory by default**. Match V1's "ask" gate in
   the isolated test profile before repository delegation.
4. V2 native provider config uses providers (plural), a package
   and settings.baseURL; a model's capabilities.tools,
   capabilities.input and capabilities.output replace tool_call
   and modalities. However V2 is intended to normalize supported
   V1 config in memory. Do NOT convert the live OpenCode 1 profile
   unnecessarily or point V1 at a V2-native-only config.
5. V2 API/SDK is a moving target: prefer a freshly running V2
   /api/info and published OpenAPI, not the older generated 61-op
   snapshot. Current public docs describe more than 100 operations.
   Native V2 /api/info must be JSON-shaped ServerInfo; current
   compatibility server at 4196 serves HTML there, even when its
   /global/health succeeds. HTTP 200 alone is NOT version proof.
6. V2 client-server runs and existing user profile can have
   separate state/DB migration concerns. Test in isolated
   XDG config/data/state/cache before any profile migration.
   Preserve the user's live sessions, installed providers,
   secret store, MCP accounts and plugin state.

## Actual preparations implemented

- Added scripts/probes/opencode-v2-preflight.py: read-only
  server/config audit, **no secrets printed**; optional write-only-new
  V2 Pixel-provider preview. It refuses unsafe endpoint drift,
  unexpected backend package or newly granted tool capability.
  It cannot overwrite active config or an existing preview.
- Four deterministic Python tests in
  scripts/probes/test_opencode_v2_preflight.py. They check:
  no credential/plugin copying, native V2 model ID/capabilities,
  fail-closed behavior, no overwrite, HTML-vs-JSON detection.
- Created .meristem/opencode-v2-pixel-preview.json containing only
  the proved local LiteRT LFM2.5 model, a short alias,
  package @opencode/ai/providers/openai-compatible and
  settings.baseURL http://127.0.0.1:18080/v1.
  It sets capabilities.tools=false and a V1-equivalent
  external_directory ask policy. **This is only a candidate,
  not yet validated by a running V2 runtime.**
- RikkaHub OpenCodeHttpClient now probes /api/info ONLY when a
  V1 request returns HTTP 404, and if native V2-shaped JSON is
  present it reports an explicit unsupported-version error.
  It does NOT replay the prompt or silently fall back to a
  similarly named V2 route. Two Kotlin regression tests verify
  fail-closed behavior and reject V1 HTML fallback as V2.
  Full app JVM suite: **1,497 tests in 215 suites, zero failures**;
  :app:assembleDebug passed offline. Four preflight Python tests
  passed. This is a **safe mismatch guard, not full V2 workspace support**.
- OpenCode 1 inference provider and RikkaHub's independent
  Ktor LiteRT/llama.cpp server remain unchanged. The Pixel ↔
  desktop Tailscale model bridge continues to use its own
  OpenAI-compatible contract, not the OpenCode session API.

## Rehearsal commands (do not migrate the live profile)

    cd ~/loam/rikkahub-local
    PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
      -s scripts/probes -p test_opencode_v2_preflight.py -v
    python3 scripts/probes/opencode-v2-preflight.py
    ./gradlew :app:testDebugUnitTest \
      --tests 'me.rerere.rikkahub.data.opencode.OpenCodeHttpClientTest' \
      --offline

The preview under .meristem is an auditable source-controlled
template, not an alternate authentication source. The configured
Pixel model requires ADB forwarding until a separately authenticated
network transport is qualified.

## Next verified gates, in order

1. Once a V2 binary is available, install/run it under an isolated
   profile and **different port**; no reuse of 4196 and no
   conversion of V1's DB in place.
2. Validate that the isolated V2 provider preview loads and produces
   an actual Pixel LiteRT answer, with tools demonstrably disabled.
   Confirm any provider-specific text-only capabilities and limits.
3. Implement/test a **separate native V2 workspace adapter**
   against live V2 OpenAPI: project/session discovery, prompt
   admission vs completion, durable event replay with sequence/cursor,
   approval handling, cancellation, auth and disconnect recovery.
   Keep V1 adapter until feature parity has receipts.
4. Port only the needed V1 plugins (9 configured, not assumed
   compatible); verify MCP discovery, model selection, and
   permissions before regular V2 coding work.
5. Confirm the background service isn't unintentionally exposed
   on all interfaces. Prior OpenCode 1 port 4196 was observed on
   0.0.0.0 with a session GET responding without an auth challenge
   locally. Do not publish either version on the tailnet until
   authentication and bindings have been audited.
6. Switch RikkaHub connections by **explicit capability selection**
   once the real V2 implementation passes regression and device
   smoke tests. No automatic production cutover.

Never treat an accepted V2 prompt as a completed model answer or
replay an uncertain side-effecting tool call. Existing V1 operation
and independent inference gateways are the fallback.
