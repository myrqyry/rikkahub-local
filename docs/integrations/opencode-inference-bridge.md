# OpenCode and RikkaHub Local model inference — October 15, 2026

## Working: desktop OpenCode SDK → phone local LiteRT

RikkaHub's existing Ktor server now includes:
- GET /v1/models — only enabled, registered local text-chat models
- POST /v1/chat/completions — one-shot and OpenAI-compatible SSE streaming

These routes call ProviderManager's native LiteRT/llama.cpp model provider
rather than ChatService; they do not create a conversation or execute
tools. They accept only text system/developer/user/assistant messages,
a bounded history and a single completion. Unsupported functions,
multimodal contents and unsupported formats return HTTP 400. A token
limit is requested through TextGenerationParams; the native runtime
remains authoritative for its actual token-count behavior.

### Trust boundary

If JWT authentication was installed when the Ktor server started, the
new routes live inside its existing JWT-authenticated route group.
Otherwise the configured server must be loopback-only AND the real
incoming client socket must have a loopback address. LAN requests fail
closed. Never expose an unauthenticated model server to the tailnet
or public internet. The USB ADB tunnel is temporary dev transport.

### Test facts

- The debug APK was built and installed in place (adb install -r);
  package excp.rikkahub.local.debug, no reset/uninstall. Private app
  databases/settings were backed up and archive integrity verified,
  with installed model files excluded from backup and unchanged.
- From desktop through adb forward tcp:18080 tcp:8080, GET /v1/models
  returned configured LFM2.5 1.2B INT4, InternVL3 1B and
  PaddleOCR-VL 1.6 LiteRT model entries.
- Native Pixel LiteRT LFM2.5 1.2B INT4 actually returned the text
  orchid for the prompt requesting that single word. Six SSE events
  ending in DONE; 24 seconds including cold model startup.
- OpenCode's real @ai-sdk/openai-compatible provider library
  generated paprika through the same phone endpoint in about one
  second on a warm model. This is genuine model inference on Pixel.
- Supplying a tool definition was rejected by the installed app
  with HTTP 400, without delegating or calling a tool.
- Full Gradle: local-llm 507, ai 200, app 1495 JVM tests; zero
  errors/failures; debug APK assembled.
- CLI command opencode models rikka-pixel --refresh lists:
  rikka-pixel/litert/LFM2.5-1.2B-Instruct_int4.litertlm
- A full opencode run CLI test, including an isolated bare config,
  timed out during OpenCode initialization before requesting the
  phone endpoint. Thus a complete CLI agent session is NOT proven;
  OpenCode model discovery and its actual model-provider SDK ARE.

The user's global OpenCode config preserves all existing providers,
MCP entries and other settings. A separately named rikka-pixel provider
was added, targeting http://127.0.0.1:18080/v1 and declaring
tool_call false for the validated model. The prior config has a local
timestamped backup under ~/.local/state/meristem/opencode-backups.

Reconnect after USB disconnect:

    cd /home/myrqyry/loam/rikkahub-local
    scripts/probes/connect-rikka-pixel-inference.sh
    opencode models rikka-pixel --refresh

A running OpenCode server or model picker may need refresh/restart;
the existing server was not restarted. No OpenCode repository/worktree
files were changed (its existing uncommitted UI changes were preserved).

## Other direction: phone → desktop (verified after reconnect)

At the previous checkpoint, the Pixel had no Tailscale VPN route
and RikkaHub's enabled tailscale provider had no registered models.
**Those two blockers are now resolved.**

Verified independently from the Pixel 10 Pro:
- Its route to 100.85.12.14 uses tun0, with phone tailnet IP 100.84.0.18.
- The desktop Tailscale MagicDNS hostname resolves on the phone.
- Phone-originated HTTPS GET /v1/models returned HTTP 200 and the
  desktop Ollama model catalog. This request ran inside Android
  using a disposable app_process Java/Dex probe, not desktop curl.
- Phone-originated HTTPS POST /v1/chat/completions targeting
  granite4.1:3b returned HTTP 200 and generated the exact text cedar.
  The temporary Android DEX was removed after testing.

Registered only the tested model on the existing enabled OpenAI
provider named tailscale:
- Model ID: granite4.1:3b; display name: Desktop · Granite 4.1 3B
- Registered model UUID: 634a202c-e1ba-4d6f-9aeb-aed77c7efcd1
- Kept the original provider ID, base URL and capabilities.
- All other 28 providers and unrelated settings compared
  byte-for-byte equal in the browser-safe before/after projections.
- Verified backup BEFORE the change:
  ~/.local/state/meristem/rikkahub-backups/pre-tailnet-model-20261015-234338.tar.gz
  and a SHA-256 sidecar; no model files were touched.

### RikkaHub's own provider acceptance check

Added explicit opt-in Android test:
app/src/androidTest/java/me/rerere/rikkahub/data/ai/DesktopOllamaOffloadDeviceTest.kt

The test loads SettingsStore and ProviderManager from RikkaHub's
real Koin container, selects the existing tailscale OpenAI provider
and its registered granite4.1:3b model, and calls generateText with
a plain text prompt, without using the conversation/agent loop.
It is skipped by default unless the instrumentation argument
tailnetInference=enabled is supplied. It does not change
preferences, assistants, conversations, tools or model files.

Build: Gradle :app:assembleDebugAndroidTest --offline — PASS.
Installed only the test APK with adb install -r, then manually ran:

    adb shell am instrument -w -r -e tailnetInference enabled       -e class me.rerere.rikkahub.data.ai.DesktopOllamaOffloadDeviceTest       excp.rikkahub.local.debug.test/androidx.test.runner.AndroidJUnitRunner

JUnit result: OK (1 test), zero failures. Phone logcat reported
DesktopOllamaOffload: remote_provider=OpenAI desktop_model=granite4.1:3b
text=willow elapsed_ms=547. This proves real remote inference
through RikkaHub's own provider; 547ms is a warm provider-call
observation, not an SLA or a guarantee of future latency.

NEVER use the Gradle connectedDebugAndroidTest task on this
user-data-bearing Pixel: that automated runner can uninstall
the target app during cleanup. Use the manual instrumentation
path above, as documented in the test source.

After instrumentation, the normal debug app process was relaunched.
Verified its existing loopback web server on port 8080 came
back, GET /v1/models returned HTTP 200 with the same three local
models, and the existing LFM2.5 model file remained intact.
The tailscale provider was still enabled with the Granite model.
No production app APK was reinstalled for this step.

The earlier inconclusive ADB reverse mapping on port 11436 was
removed. Unlike the independent desktop-to-Pixel bridge, the
phone-to-desktop route now runs over Tailscale without needing USB.
Both are model-inference paths, **not** automatic selection between
devices or two-way agent delegation.

## Future gates

- Add robust streaming-error and request-abort/cancellation tests.
- Investigate full OpenCode CLI startup independently of the SDK.
- Build an authenticated, persisted device-to-device connection before
  enabling mobile offload away from USB.
- Distinguish model inference from OpenCode or RikkaHub agent delegation,
  preserve one tool executor, explicit permissions and no cycles.
- Real scheduling by device readiness, RAM, thermals, latency and user
  privacy is not implemented. No split-KV or split-layer inference.
- Separate issue: desktop OpenCode server port 4196 was observed
  listening on 0.0.0.0, and the session endpoint returned HTTP 200
  without auth in a local check. Don't reuse that as a remote model
  gateway without independently securing its access.

No push or history rewrite was performed.
