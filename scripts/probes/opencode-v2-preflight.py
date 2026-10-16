#!/usr/bin/env python3
"""Read-only V2 bridge audit and optional non-secret, isolated provider preview.

This deliberately does not install V2, edit OpenCode's V1 profile,
restart services, touch credential stores, or upgrade the RikkaHub app.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

PACKAGE = "@opencode/ai/providers/openai-compatible"
PROVIDER_ID = "rikka-pixel"
V2_ALIAS = "lfm2.5-1.2b"
PROBE_TIMEOUT = 3


def pixel_preview(config: dict) -> dict:
    """Create a *standalone* V2 config for the one already-proven ADB model.

    No upstream credentials, other providers, MCP servers or agent settings are
    copied. Never silently reinterpret a different provider as our device.
    """
    old = config.get("provider", {}).get(PROVIDER_ID)
    if not isinstance(old, dict):
        raise ValueError("No V1 rikka-pixel provider; do not fabricate connection")
    if old.get("npm") != "@ai-sdk/openai-compatible":
        raise ValueError("Unexpected Pixel provider package; requires review")
    url = old.get("options", {}).get("baseURL")
    if url != "http://127.0.0.1:18080/v1":
        raise ValueError("Pixel provider no longer uses the verified ADB loopback endpoint")
    candidates = [
        (key, value) for key, value in old.get("models", {}).items()
        if key.startswith("litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        and isinstance(value, dict) and value.get("tool_call") is False
    ]
    if len(candidates) != 1:
        raise ValueError("Validated LiteRT LFM2.5 model missing or tool permission changed")
    original_id, old_model = candidates[0]
    limits = old_model.get("limit", {})
    context, output = limits.get("context"), limits.get("output")
    if not isinstance(context, int) or not isinstance(output, int) or context <= 0 or output <= 0:
        raise ValueError("Model capacity must be explicitly known")
    return {
        "$schema": "https://opencode.ai/config.json",
        "providers": {
            PROVIDER_ID: {
                "name": "RikkaHub Pixel (ADB / LiteRT)",
                "package": PACKAGE,
                "settings": {"baseURL": url},
                "models": {
                    V2_ALIAS: {
                        "modelID": original_id,
                        "name": "Pixel LiteRT · LFM2.5 1.2B",
                        "capabilities": {"tools": False, "input": ["text"], "output": ["text"]},
                        "limit": {"context": context, "output": output},
                    }
                },
            }
        },
        # V2 currently allows external_directory by default; preserve
        # V1's opt-in/ask posture for isolated agent experiments.
        "permissions": [{"action": "external_directory", "resource": "*", "effect": "ask"}],
    }


def probe_server(url: str) -> tuple[str, str]:
    """Classify health evidence; a 200 response alone is *never* V2 proof."""
    try:
        req = urllib.request.Request(url.rstrip("/") + "/api/info", headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=PROBE_TIMEOUT) as r:
            mime = r.headers.get_content_type()
            payload = r.read(131072)
        if mime == "application/json":
            info = json.loads(payload)
            if isinstance(info, dict) and any(key in info for key in ("urls", "paths", "ready")):
                return "v2-candidate", "native V2-shaped /api/info; requires endpoint contract test"
            return "unknown", "JSON response is not a recognized native V2 ServerInfo"
        if mime == "text/html":
            return "not-v2", "/api/info serves HTML (may be the V1 web app)"
        return "unknown", "Unexpected /api/info content type"
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
        return "unverified", "API probe failed: " + type(error).__name__


def write_candidate(candidate: dict, path: Path, source: Path) -> None:
    destination = path.expanduser().resolve()
    if destination == source.expanduser().resolve():
        raise ValueError("Refusing to overwrite the active OpenCode configuration")
    destination.parent.mkdir(parents=True, exist_ok=True)
    import os
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        json.dump(candidate, output, indent=2, ensure_ascii=False)
        output.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path.home() / ".config/opencode/opencode.json")
    parser.add_argument("--server", default="http://127.0.0.1:4196")
    parser.add_argument("--write-preview", type=Path, help="Optional NEW output path, never a live config")
    args = parser.parse_args()

    config = json.loads(args.config.expanduser().read_text(encoding="utf-8"))
    family, reason = probe_server(args.server)
    print("OpenCode V2 readiness (read-only)")
    print("  server:", family, "—", reason)
    print("  current config shape:", "V1 provider" if "provider" in config else "not V1 provider")
    print("  V1 provider count:", len(config.get("provider", {})))
    print("  existing plugins:", len(config.get("plugin", [])), "(port hooks before V2)")
    print("  Pixel inference path:", "ADB loopback; independent of OpenCode server API")
    try:
        preview = pixel_preview(config)
        print("  V2 Pixel candidate:", PROVIDER_ID + "/" + V2_ALIAS)
        print("  tool execution:", "disabled")
        print("  external_directory:", "ask in candidate")
        if args.write_preview:
            write_candidate(preview, args.write_preview, args.config)
            print("  isolated preview saved:", args.write_preview)
    except ValueError as e:
        print("  V2 Pixel candidate requires review:", e)
        return 2

    print("  WARNING: V2 session API is breaking; RikkaHub workspace client is V1-only.")
    print("  WARNING: no automatic OpenCode service, config, or database migration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
