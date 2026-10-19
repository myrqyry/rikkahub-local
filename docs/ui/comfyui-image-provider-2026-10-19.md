# ComfyUI provider — RikkaHub image generation (2026-10-19)

## Intent

Integrate the user's existing ComfyUI into RikkaHub's **existing** image
generation UI, model selection, agent image tool, gallery and receipts.
Do not create a second disconnected gallery or assume arbitrary ComfyUI
graphs can be inferred from a user prompt.

## Verified host evidence

On yuzu-mint (the authorized workstation), a real ComfyUI server is running
at \`http://127.0.0.1:8188\`, bound **only** to desktop loopback. GET
\`/system_stats\` reports ComfyUI 0.37.0, GET \`/models/checkpoints\`
returns actual installed checkpoints (e.g. DreamShaper 8 and other local
models). Read-only GET \`/object_info/{node}\` returned HTTP 200 and
matching node descriptions for all six required node types:
CheckpointLoaderSimple, CLIPTextEncode, EmptyLatentImage, KSampler,
VAEDecode, SaveImage. No server listening changes were made.

## Implemented end-to-end text-to-image contract

- Added serializable, **disabled-by-default** \`ProviderSetting.ComfyUi\`
  and stable ID; seeded a stable \`IMAGE\` model with \`IMAGE_GENERATION\`
  capability, not \`IMAGE_EDITING\`. Existing providers and selections
  are not overwritten. Registered real \`ComfyUiProvider\` in ProviderManager
  and app DI. User can select this model in the existing Image page or
  model role assignments after explicitly enabling the provider.
- Per-provider editing: server URL, **Test connection / Load checkpoints**
  (actual /system_stats and /models/checkpoints), exact checkpoint
  selection, negative prompt, width, height, steps, CFG, seed, sampler and
  scheduler. Numeric entry preserves editing drafts. This is a
  **standard txt2img graph**; not an arbitrary workflow importer.
- Generates a known CheckpointLoaderSimple → CLIPTextEncode positive and
  negative → EmptyLatentImage → KSampler → VAEDecode → SaveImage graph,
  bound from real user prompt, aspect ratio, configured sampling parameters
  and image count. Submitted to POST /prompt, polled via GET
  /history/{prompt_id}, loaded output images through GET /view. Final
  ByteArray images flow into the existing GenerationService, ImageMediaStore
  and gallery, rather than a separate UI or fabricated local files.
- Max four requested images; no fabricated partial-preview frames.
  Polling waits until the server reports completed output; errors surface
  rather than being treated as success. A 10-minute client completion
  deadline and 40 MiB per-image response limit apply. Paths stay pinned
  to the configured ComfyUI host; output filenames are encoded as
  query parameters, not interpolated into URL paths.
- On local cancellation, best-effort POST /queue delete targets only
  the queued prompt. **No global /interrupt** (which could stop another
  user's active ComfyUI work). If already executing, the server job may
  continue even after the phone stops waiting.
- Generation receipts report "comfyui"/"self-hosted HTTP" rather than
  pretending Android rendered the image. Other providers keep their
  prior behavior. A ComfyUI host is remote relative to the phone and
  therefore still respects the assistant's permission to process images
  through a network service.

## Validation

- \`./gradlew :app:testDebugUnitTest :app:assembleDebug\`:
  **BUILD SUCCESSFUL**, 1,535 JVM tests across 224 suites, no failures,
  errors or skips. Debug arm64 APK built successfully.
- Six new HTTP fixture tests cover a request containing a real seven-node
  graph, /prompt → /history → /view and PNG bytes, checkpoint discovery,
  bad server workflow response, invalid sampling/checkpoint inputs,
  refusal to synthesize while disabled, and URL credential restrictions.
  Mock responses are explicitly **not** a real ComfyUI diffusion execution.
- A private app database/datastore/preferences backup was created on the
  Pixel and its SHA-256 checked. Installed debug APK with \`adb install -r\`
  (Success), no uninstall, reset or model writes.
- A temporary \`adb reverse tcp:8188 tcp:8188\` was created to reach
  desktop loopback from the Pixel without exposing the ComfyUI service
  publicly. Pixel netcat's socket-connect check returned success, but an
  HTTP response through that shell path was not captured. The device was
  locked during the first UI attempt and later became unlocked; a second
  navigation attempt entered an existing unrelated model-source screen.
  **Provider editor, live checkpoint list on Pixel, and real image generation
  on Pixel are not yet verified.** Do not overstate this as a complete
  real-device workflow smoke.

## Deliberate scope / follow-up

- **No image-to-image, inpainting, custom workflow JSON, LoRA asset browser,
  WebSocket live preview, queue-position UI, arbitrary node discovery or
  GPU execution benchmarks yet.** Such features need workflow-specific
  bindings and separate tests; showing inert controls would be worse.
- The user's ComfyUI server currently listens on desktop localhost only.
  Without ADB reverse, a persistent phone connection needs an explicitly
  authorized network-access path or an existing authenticated tunnel.
  **Do not change the server bind address automatically.**
- Next device test: navigate Model Sources → ComfyUI, press Test connection
  and select a real checkpoint without altering other providers; save
  intentionally only when user approves. Then run one bounded job (e.g.
  known checkpoint, modest resolution), verify gallery output, error and
  cancellation semantics and ComfyUI history. Verify privacy permission
  for network-based images before making a generation request.
- Repo branch contains unpublished changes, so commit locally and do
  not push simply because verification passed.


## Follow-up: documented ComfyUI WebSocket events (2026-10-19)

Source references:
- https://docs.comfy.org/development/comfyui-server/comms_overview
- https://docs.comfy.org/development/comfyui-server/comms_messages
- https://docs.comfy.org/development/comfyui-server/comms_routes
- https://docs.comfy.org/development/comfyui-server/api-examples

Implemented a dedicated WebSocket per image generation request, connecting
to /ws?clientId=<UUID> before POST /prompt uses the same client ID.
OkHttp newWebSocket upgrades a normal HTTP(S) request: explicitly changing
the HttpUrl Builder scheme to ws/wss is invalid and was corrected.

The callback parser classifies execution_start, executing, progress,
execution_success, execution_error and execution_interrupted. Global
status messages are ignored because queue_remaining is not per-job.
The executed message is also ignored as a general completion event:
ComfyUI documents that it fires only for node UI output. Progress/events
are matched against the server-returned prompt_id. The in-app Images UI
shows actual workflow stage and KSampler steps (value/max), not a made-up
percentage of the whole graph. Each progress update is guarded by a
locally generated run ID to avoid stale-run updates.

The WebSocket is primary for event notifications. Polling /history
periodically on missed/dropped events or connection loss is the recovery
path; the image metadata and bytes still come from /history and /view.
Cancellation is queued-only and does not call global /interrupt.

The docs distinguish binary WebSocket preview image frames from JSON
progress events. Binary preview frames are explicitly skipped for now.
No live image preview, custom node workflows, LoRA graph UI, or img2img
has been implemented by this follow-up.

Verification:
- Full :app:testDebugUnitTest :app:assembleDebug passed: 1,541 JVM tests
  across 225 suites, 0 failures/errors/skips.
- Focused tests include typed event parsing, ignored global/other-job
  events, cancellation-safe status guards, and a real loopback OkHttp
  WebSocket handshake/message-delivery test using a tiny local server.
- Original HTTP-only fixture tests now require a /ws handshake attempt,
  demonstrating history fallback when a socket upgrade fails.
- Live ComfyUI on yuzu-mint accepted a read-only WebSocket HTTP 101
  Switching Protocols handshake. No inference/job was queued.
- Verified SHA-256 of a private Pixel app-data backup, then installed
  APK in place using adb install -r (Success). No app-data reset,
  external server configuration or networking exposure occurred.
  Real phone generation, partial preview, GPU performance and
  gallery end-to-end still require a separate model-backed test.
