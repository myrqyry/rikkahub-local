#!/usr/bin/env bash
# Connect OpenCode to an authorized Pixel's existing loopback-only RikkaHub
# inference server. Does not install apps or change user/model data.
set -euo pipefail
adb_bin="${ADB:-adb}"
command -v "$adb_bin" >/dev/null || { echo "adb not found" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl not found" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 2; }
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  serial="$("$adb_bin" devices | awk 'NR>1 && $2=="device" {print $1}')"
  if [[ "$(printf '%s\n' "$serial" | grep -c '.' || true)" -ne 1 ]]; then
    echo "Set ANDROID_SERIAL when multiple devices are connected" >&2
    exit 2
  fi
fi
existing="$("$adb_bin" -s "$serial" forward --list | awk '$2=="tcp:18080" {print $3}')"
if [[ -n "$existing" && "$existing" != "tcp:8080" ]]; then
  echo "TCP 18080 has an unrelated ADB forward; refusing to replace it" >&2
  exit 2
fi
if [[ -z "$existing" ]]; then
  "$adb_bin" -s "$serial" forward --no-rebind tcp:18080 tcp:8080 >/dev/null
fi
curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1:18080/v1/models |
  python3 -c '
import json,sys
j=json.load(sys.stdin)
models=[x["id"] for x in j.get("data", [])]
if not models: raise SystemExit("No local models exposed")
print("RikkaHub local inference is reachable. Models:")
for model in models: print("  "+model)
'
echo "OpenCode provider: rikka-pixel (ADB)"
echo "Inference base URL: http://127.0.0.1:18080/v1"
echo "Keep the phone connected or run this script again after reconnecting."
