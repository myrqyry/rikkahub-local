# Device MCP export

RikkaHub Local can act as an Android MCP node through its existing foreground web server.

The implementation deliberately reuses the app's existing local-tool selection instead of
creating a second device-permission registry. Remote export is a narrower capability boundary
on top of those assistant-local choices.

## Enable it

1. In the active assistant, enable any local read-only tools you want available.
2. Open Web Server settings and set an access password.
3. Enable authentication.
4. Enable **Expose device tools over MCP**.
5. Start the web server.
6. Copy the **MCP Endpoint** shown in settings.

The MCP route is `/mcp`. Device tools are never exported from a route that was mounted without
JWT authentication, even if settings are changed while that server process is still running.

## Authentication

The existing web authentication flow remains the single authority. Obtain a web access token
from `POST /api/auth/token` using the configured password, then send the returned token as:

`Authorization: Bearer <token>`

No PocketMCP-specific API key, secondary credential store, or duplicate server is introduced.
## Initial remote surface

Only these local tools are eligible for remote export, and each one must also be enabled on the
current assistant:

- `rikka.device.get_time_info`
- `rikka.device.get_battery_status`
- `rikka.device.get_storage_info`
- `rikka.device.get_brightness`
- `rikka.device.get_volume`

The explicit allowlist is intentionally smaller than "all read-only tools." Wi-Fi identifiers,
audio device names, media metadata, installed-app inventory, sensors, notifications, contacts,
messages, call logs, location, files, camera/microphone capture, accessibility/UI control,
app launching, shell/Termux, and other action tools are not exported.

## Invariants

- Device export defaults off.
- It requires both startup-route authentication and current JWT-enabled state.
- Turning authentication off or clearing the password disables device export in settings.
- The remote allowlist is independent of the assistant's local-tool list; local enablement can
  narrow the remote set but can never widen it.
- Remote tool names use the `rikka.device.` namespace to avoid collisions with relayed MCP tools.
- Action/sensitive tools need a dedicated remote grant and approval design before being added.

The implementation lives in `web/mcp/RikkaDeviceMcpTools.kt` and is served by the existing
`StatelessMcpRoute`.
