# RikkaHub + RikkaHub Agent upstream review — 2026-10-08

Local checkout: `/home/myrqyry/loam/rikkahub-local`, `aster/device-execution-profile`.
Sources checked live through the GitHub API (commit list, detailed patches, releases)
against `rikkahub/rikkahub` and `ExTV/rikkahub-agent`. Do not infer that code in
an upstream public branch applies unchanged to our significantly extended fork.

## Confirmed upstream heads / baseline

- Official `rikkahub/rikkahub` master head `eebdea0bd5c8` dated 2026-10-08
  (latest published release `2.5.6`, 2026-10-01). Subsequent commits Oct 6-8
  include MD3 Expressive changes, sketch canvas + stylus/selection features,
  conversation-level pinned assistant configuration, summary checkpoints during
  compaction, model identity retention, settings-store lost-update fixes and
  recent generation-provider changes.
- Agent fork `ExTV/rikkahub-agent` master head `a88f5854b4d2` dated
  2026-09-24; latest released `v2.5.1-agent.1` published 2026-09-24.
  Its September release adds OpenRouter image API work and fixes mixed tool
  approval/resume, interruption semantics, Shizuku retry, restored-database
  crash loop, streamed image parts, prompt cache churn and source-stream leaks.

## Implemented here (source-derived, not direct cherry-picks)

1. **OpenAI Responses optional arguments — fully host-qualified.**
   Upstream official `a6dbb8cd2ba8` (2026-10-03) sets `strict: false` on
   function tools to preserve optional JSON-schema parameters. Our
   `ai/.../ResponseAPI.kt` omitted it. Added it with a request-JSON
   regression asserting an optional property stays optional.
   `./gradlew :ai:testDebugUnitTest :app:assembleDebug` passed;
   log `/tmp/rikkahub-upstream-response-strict-full-20261008.log`.
   This tests serialization and Android compilation, **not** a live
   third-party provider call.
2. **Agent file-stream cleanup — compiled but app test blocked.**
   Agent upstream `0ff796986ff4` (2026-09-24) wraps the whole copy/move
   destination-validation and data-transfer path inside `srcStream.use`.
   Our `FileManagerContentOps.moveOrCopyContent` opened the source before
   checking the destination, leaving it unclosed on an early refusal.
   Backported source lifetime and added an OS-gated `/proc/self/fd` negative
   regression in `FileManagerToolTest` for an existing destination.
   `./gradlew :app:assembleDebug` passed after this change;
   `/tmp/rikkahub-main-agent-upstream-app-build-20261008.log`.
   The app test task `:app:compileDebugUnitTestKotlin` failed before test
   execution **twice**, with Gradle 9.4.1 `Failed to create MD5 hash
   for file: Invalid file path` when snapshotting `destinationDirectory`;
   `--no-configuration-cache` did not help. Log:
   `/tmp/rikkahub-agent-file-safety-rerun-20261008.log`;
   stacktrace `/tmp/rikkahub-upstream-test-compile-trace-20261008.log`.
   Don't report the new FD regression as passing. Avoid deleting other
   build state to force a green result without investigating the source.

## Next high-value backports (NOT done; verify integration first)

1. **Settings atomicity, top priority:** official `3462d9ee18e4` and
   `4dbf303f8dd1` (Oct 7) make the in-memory settings authoritative,
   normalize every update under a lock, serialize/coalesce disk persistence,
   and eliminate UI writebacks of whole stale snapshots. Current fork still
   has `SettingsStore.update(settings: Settings)`,
   `settingsFlowRaw` readback, and UI sites in `ChatVM`,
   `SettingVM`, `AssistantVM`, theme/chat pages that update snapshots.
   It has `update(fn)` with a transform mutex, but whole-snapshot UI
   callers still bypass it; an isolated mutex tweak won't solve that.
   Stage on separate worktree, preserve all custom settings keys and restore
   migrations, add racing field-update tests, test rapid text/slider changes,
   validate backups/restore, and ensure no transient default state writes.
2. **Tool replay and cancellation:** Agent `1913333a6793` prevents
   re-execution of auto-approved tools interrupted after they began; `1c2ca2dcbd44`
   resumes never-started auto siblings after mixed approval; `0658ab587d4e`
   preserves cancellation exceptions instead of manufacturing tool failures;
   `f85ab6670ee7` limits duplicate title/suggestion jobs against local
   servers. Our fork's agent execution pipeline and durable run receipts
   have diverged from `GenerationLoop.kt`; inspect current
   `AgentRun` and tool execution markers before adopting. Re-execution
   of a side-effecting tool is safety-relevant: unknown outcomes must not
   automatically replay.
3. **Conversation continuity:** official `bf614d89a8e3` creates a
   summary checkpoint in the message graph instead of deleting original
   messages; `b64beeef6e34` pins model/search/reasoning config to each
   conversation; `4a7a39c47281` preserves displayable model identities
   after removing a provider. These fit our continuity goals but require
   migrations, branch/edit/regenerate/export and web DTO tests.
4. **Search:** official `c211c0d447f4` exempts `search_web` structured
   results from generic tool-output truncation and falls back to raw text if
   a custom preview can't decode JSON. Our fork uses a distinct tool output
   / retrieval pipeline. Inspect its search renderer and provenance
   before directly porting.
5. **Optional UI:** Oct 6-8 stylus-sensitive sketch canvas, media creation
   and MD3 Expressive surfaces. Borrow the drawing/annotation **mechanism**
   if useful for multimodal prompting; avoid wholesale theme replacement.
6. **Already present in some form, don't duplicate:** OpenAI-compatible
   streamed image URI handling (our `ChatCompletionsAPI.kt` calls
   `parseImageDataUri`), MCP OAuth 2.1 client, and extensive local runtime,
   agent receipt and workspace capabilities. Presence alone is not
   evidence their full upstream regression tests are passing.

## Execution boundary

All changes local only. No app installation, credentials, data migration,
upstream merge or push. Actual phone and provider integration remain
unverified. Preserve independent LiteRT 2.3.0 qualification worktree.
