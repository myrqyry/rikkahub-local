# Two-upstream selective harvest — October 15, 2026

**Lineage:** RikkaHub Local is an independent fork of ExTV/rikkahub-agent,
itself descended from rikkahub/rikkahub. Neither tree is a merge target.
Read the prior .meristem/upstream-review-2026-10-08.md before further
upstream work.

**Pinned heads:** original master 240deb5fe (Oct 15);
Agent master a88f5854b (Sep 24); local pre-harvest 330f63073.

## Verified adaptations

- **63d3c6826** (original commits 923ad3d91 and 240deb5fe):
  Room message-node paging rethrows CancellationException before the
  recoverable IllegalStateException path, preventing a cancelled query
  from looping inside a transaction. OpenAI-compatible SSE processing
  skips JSON null/non-object lines but still rejects malformed JSON;
  annotation decoding skips null/non-object entries and handles a null
  citation object. **3 focused JVM tests** pass. No live network test,
  nor simulated Room transaction-cancellation test.
- **1aef8a3b6** (adapted from Agent f85ab6670):
  Background title/suggestion model requests are tracked separately by
  conversation and job kind and replaced/cancelled when obsolete.
  Entry points include new user input, regeneration, new model turn,
  stop, session deletion and cleanup. Completion removes only its own
  registration so a replaced job cannot deregister the successor.
  Preserved local conversation reference lifecycle and agent runtime.
  **5 focused JVM tests** pass; no live inference-pressure timing yet.
  Existing executionStartedAt replay protection was already present.
- **b6bb5e7b6** (original cf79246bf):
  MCP tool JSON schemas now expand local JSON Pointer references in
  schema-bearing fields rather than discard definitions when converting
  to the local InputSchema.Obj model. Keeps literal enum/default/example
  data intact, preserves root required fields and bounds recursion.
  **6 focused JVM tests** pass. Remains on the existing MCP SDK 0.14.0;
  no claim of live third-party server/provider validation.

## Full verification

- Gradle :ai:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
  --offline --console=plain => BUILD SUCCESSFUL (16 seconds).
- AI: 200 unit tests, 0 failed/error/skipped.
- App: 1,486 unit tests, 0 failed/error/skipped.
- Android debug APK packaged; no install or device runtime test.
- Source diff whitespace checks passed. No merge, push, release,
  live provider request, app-data operation or model change.

## Not carried over and why

- Original settings-store atomicity 3462d9ee1/4dbf303f8/640496127:
  our fork has a transform mutex, but UI code still passes stale whole
  snapshots to update(Settings). High value, **high persistence risk**;
  isolate and test concurrent field edits, rapid sliders/text, custom
  settings fields, backups and restore before modifying.
- Agent database restore migration 787589eb0, original backup recovery
  540b9dfaf: database schema and installed user data differ; first
  validate on disposable copies with verified backups.
- MCP SDK/OAuth upgrade, toast library migration: test runtime API
  compatibility and Android interaction separately.
- Original stylus drawing canvas and Material 3 Expressive components:
  promising mechanisms for our multimodal/visual workflows, but design
  work rather than a correctness backport.
- Memory, context compaction and agent loop: architectural divergence;
  retain our own model/receipt authority, evaluate mechanisms individually.

Next: isolate settings lost-update correctness and test for safe migration.
If user wants it installed on the Pixel, obtain explicit authorization
for the in-place replacement and verify existing app data backup.
