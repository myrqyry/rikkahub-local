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

## Other direction: phone → desktop

RikkaHub on the phone already has an enabled OpenAI-compatible provider
named tailscale whose base URL is the desktop's Tailscale Serve
Ollama endpoint. However it has zero configured models at inspection,
and the Pixel currently could not resolve its MagicDNS hostname or
connect to the desktop tailnet IP. Android connectivity did not show
an active VPN route. Desktop Ollama's localhost API listed 20 models,
and Tailscale Serve itself responded from the desktop via a correct
hostname-to-tailnet-IP binding. The phone route is NOT proven.

An exploratory adb reverse mapping from phone tcp:11436 to desktop
tcp:11434 was set up, but a phone netcat HTTP test returned no
response. No successful reverse inference claim. This unverified
reverse mapping should be removed. Do not replace the user's
configured tailscale provider with a USB-only URL. First reestablish
the phone's Tailscale connectivity with user involvement and validate
DNS, TLS and a real model response from the phone; then add selected
models to RikkaHub.

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
