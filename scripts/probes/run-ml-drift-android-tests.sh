#!/usr/bin/env bash
# Runs built upstream ML Drift Android ARM64 tests in temporary device storage.
# Never installs an app or changes private application/model data.
set -euo pipefail
if test "$#" -lt 1; then
  echo "Usage: ANDROID_SERIAL=your-adb-serial $0 /path/to/android-test-binary [...]" >&2
  exit 2
fi
adb_bin="$(printenv ADB || echo adb)"
command -v "$adb_bin" >/dev/null || { echo "adb not found" >&2; exit 2; }
serial="$(printenv ANDROID_SERIAL || true)"
if test -z "$serial"; then
  serial="$("$adb_bin" devices | awk 'NR>1 && $2=="device" {print $1}')"
  if test "$(printf '%s\n' "$serial" | sed '/^$/d' | wc -l)" -ne 1; then
    echo "Specify ANDROID_SERIAL; exactly one authorized device is required." >&2
    exit 2
  fi
fi
adb_remote() { "$adb_bin" -s "$serial" "$@"; }
remote=""
cleanup() {
  if test -n "$remote"; then
    adb_remote shell rm -f "$remote" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

for binary in "$@"; do
  if ! test -f "$binary"; then
    echo "Not a file: $binary" >&2; exit 2
  fi
  # Bazel can silently emit a host x86_64 binary under Android flags.
  if ! file -L "$binary" | grep -q 'ARM aarch64'; then
    echo "Not an Android ARM64 binary: $binary" >&2
    file -L "$binary" >&2
    exit 2
  fi
  if ! readelf -l "$binary" | grep -q '/system/bin/linker64'; then
    echo "No Android dynamic linker: $binary" >&2; exit 2
  fi
  remote="$(adb_remote shell mktemp /data/local/tmp/ml-drift-verified.XXXXXX | tr -d '\r')"
  case "$remote" in
    /data/local/tmp/ml-drift-verified.*) ;;
    *) echo "Unexpected device temporary path: $remote" >&2; exit 2 ;;
  esac
  adb_remote push "$binary" "$remote" >/dev/null
  adb_remote shell chmod 700 "$remote"
  echo "--- Running $(basename "$binary") on $serial ---"
  adb_remote shell "timeout -s TERM -k 5s 120s nice -n 10 '$remote'"
  adb_remote shell rm -f "$remote"
  remote=""
done
