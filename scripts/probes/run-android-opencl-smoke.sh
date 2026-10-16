#!/usr/bin/env bash
# Reproducible Android shell OpenCL kernel probe; does not touch installed app/data.
set -euo pipefail
SCRIPTDIR="$(cd "$(dirname "$0")" && pwd)"
NDK="$(printenv ANDROID_NDK_HOME || printenv ANDROID_NDK_ROOT || true)"
if [[ -z "$NDK" ]]; then
  echo "ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) is required" >&2
  exit 2
fi
ADB="$(printenv ADB || echo adb)"
CLANG="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang++"
if [[ ! -x "$CLANG" ]]; then
  echo "Expected Linux-hosted NDK Android ARM64 compiler: $CLANG" >&2
  exit 2
fi
command -v "$ADB" >/dev/null || { echo "adb not found" >&2; exit 2; }
build="$(mktemp -d)"
remote=""
cleanup() {
  if [[ -n "$remote" ]]; then
    "$ADB" shell rm -f "$remote" >/dev/null 2>&1 || true
  fi
  rm -rf "$build"
}
trap cleanup EXIT
"$CLANG" -O2 -std=c++17 -static-libstdc++ \
  "$SCRIPTDIR/android-opencl-smoke.cpp" -o "$build/android-opencl-smoke" -ldl
remote="$("$ADB" shell mktemp /data/local/tmp/rikkahub-opencl-smoke.XXXXXX | tr -d '\r')"
if [[ "$remote" != /data/local/tmp/rikkahub-opencl-smoke.* ]]; then
  echo "Unexpected temporary path: $remote" >&2
  exit 2
fi
"$ADB" push "$build/android-opencl-smoke" "$remote" >/dev/null
"$ADB" shell chmod 700 "$remote"
"$ADB" shell timeout -s TERM -k 3s 30s "$remote"
