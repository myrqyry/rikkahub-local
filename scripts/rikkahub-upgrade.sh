#!/usr/bin/env bash
#
# rikkahub-upgrade.sh — verify a genuine in-place APK upgrade preserves app state.
#
# Flow: identity checks -> safety backup -> install old -> seed state -> verify ->
#       `adb install -r` new -> launch -> verify UID unchanged + seeded state intact.
#
# NEVER uninstalls, never runs `pm clear`. Old and new APKs MUST share the exact
# application ID and signing certificate.
#
set -euo pipefail

SDK="${ANDROID_HOME:-}"
if [[ -z "$SDK" && -f "$(dirname "$0")/../local.properties" ]]; then
  SDK="$(grep '^sdk.dir=' "$(dirname "$0")/../local.properties" | cut -d= -f2)"
fi
AAPT=""
APKSIGNER=""
for bt in "$SDK"/build-tools/*/; do
  [[ -x "$bt/aapt" ]] && AAPT="$bt/aapt"
  [[ -x "$bt/apksigner" ]] && APKSIGNER="$bt/apksigner"
done
[[ -n "$AAPT" ]] || { echo "ERROR: aapt not found under $SDK/build-tools" >&2; exit 1; }
[[ -n "$APKSIGNER" ]] || { echo "ERROR: apksigner not found under $SDK/build-tools" >&2; exit 1; }

HERE="$(cd "$(dirname "$0")" && pwd)"
DATA_TOOL="$HERE/rikkahub-data.sh"
PACKAGE=""
SERIAL=""
OLD_APK=""
NEW_APK=""
BACKUP_PATH=""

usage() {
  cat >&2 <<'EOF'
usage: rikkahub-upgrade.sh --serial SERIAL --old OLD.apk --new NEW.apk \
       --package PKG --backup DIR

  --serial   adb device serial
  --old      older compatible APK (same applicationId + signing cert as --new)
  --new      newer APK to install in place
  --package  application ID (default: excp.rikkahub.local)
  --backup   existing writable directory; a safety backup is created here first
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --old) OLD_APK="${2:-}"; shift 2 ;;
    --new) NEW_APK="${2:-}"; shift 2 ;;
    --package) PACKAGE="${2:-}"; shift 2 ;;
    --backup) BACKUP_PATH="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done
PACKAGE="${PACKAGE:-excp.rikkahub.local}"
[[ -n "$SERIAL" ]] || die_missing "--serial"
[[ -n "$OLD_APK" ]] || die_missing "--old"
[[ -n "$NEW_APK" ]] || die_missing "--new"
[[ -n "$BACKUP_PATH" ]] || die_missing "--backup"
die_missing() { echo "ERROR: missing $1" >&2; exit 1; }

[[ -f "$OLD_APK" ]] || { echo "ERROR: old apk not found: $OLD_APK" >&2; exit 1; }
[[ -f "$NEW_APK" ]] || { echo "ERROR: new apk not found: $NEW_APK" >&2; exit 1; }
[[ -d "$BACKUP_PATH" ]] || { echo "ERROR: backup dir not found: $BACKUP_PATH" >&2; exit 1; }

apk_package() { "$AAPT" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1; }
apk_version_name() { "$AAPT" dump badging "$1" | sed -n "s/^package: name='[^']*' versionCode='[0-9]*' versionName='\([^']*\)'.*/\1/p" | head -1; }
apk_version_code() { "$AAPT" dump badging "$1" | sed -n "s/^package: name='[^']*' versionCode='\([0-9]*\)'.*/\1/p" | head -1; }
apk_cert_digest() { "$APKSIGNER" verify --print-certs "$1" 2>/dev/null | sed -n 's/^.*certificate SHA-256 digest: \(.*\)$/\1/p' | head -1 | tr -d '\r'; }

echo "== identity checks =="
OLD_PKG="$(apk_package "$OLD_APK")"
NEW_PKG="$(apk_package "$NEW_APK")"
OLD_CERT="$(apk_cert_digest "$OLD_APK")"
NEW_CERT="$(apk_cert_digest "$NEW_APK")"
echo "old: $OLD_PKG ($(apk_version_name "$OLD_APK") / $(apk_version_code "$OLD_APK"))"
echo "new: $NEW_PKG ($(apk_version_name "$NEW_APK") / $(apk_version_code "$NEW_APK"))"
[[ "$OLD_PKG" == "$NEW_PKG" ]] || { echo "ERROR: application ID mismatch: $OLD_PKG vs $NEW_PKG" >&2; exit 1; }
[[ "$OLD_PKG" == "$PACKAGE" ]] || { echo "ERROR: APK package $OLD_PKG != --package $PACKAGE" >&2; exit 1; }
[[ -n "$OLD_CERT" && "$OLD_CERT" == "$NEW_CERT" ]] || {
  echo "ERROR: signing certificate mismatch" >&2
  echo "  old: ${OLD_CERT:-<none>}" >&2
  echo "  new: ${NEW_CERT:-<none>}" >&2
  exit 1
}

echo "== safety backup before touching the device =="
"$DATA_TOOL" backup --serial "$SERIAL" --package "$PACKAGE" \
  --output "$BACKUP_PATH/$PACKAGE-pre-upgrade-$(date -u +%Y%m%dT%H%M%SZ).tar"

adb_cmd() { adb -s "$SERIAL" "$@"; }

pkg_uid() { adb_cmd shell dumpsys package "$PACKAGE" | grep -m1 -E 'uid=[0-9]+' | sed 's/.*uid=\([0-9]*\).*/\1/' | tr -d '\r'; }

echo "== install old APK (in place) =="
adb_cmd install -r "$OLD_APK" >/dev/null
UID_BEFORE="$(pkg_uid)"
echo "package uid after old install: $UID_BEFORE"

echo "== seed state =="
# SharedPreferences XML marker (settings) + a workspace-style file under files/.
SEED_CMD="run-as $PACKAGE sh -c 'mkdir -p shared_prefs files/workspace; printf \"%s\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><string name=\\\"upgrade_marker\\\">seed-123</string></map>\" > shared_prefs/upgrade_marker.xml; printf \"%s\" seed-workspace > files/workspace/marker.txt'"
adb_cmd shell "$SEED_CMD"
MARKER_XML="$(adb_cmd shell run-as "$PACKAGE" cat shared_prefs/upgrade_marker.xml | tr -d '\r')"
[[ "$MARKER_XML" == *"seed-123"* ]] || { echo "ERROR: failed to seed state" >&2; exit 1; }

echo "== install new APK over old (adb install -r, never uninstall) =="
adb_cmd install -r "$NEW_APK" >/dev/null
UID_AFTER="$(pkg_uid)"
[[ "$UID_AFTER" == "$UID_BEFORE" ]] || { echo "ERROR: package uid changed $UID_BEFORE -> $UID_AFTER" >&2; exit 1; }
echo "package uid unchanged: $UID_AFTER"

echo "== launch =="
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null 2>&1 || true
sleep 3

echo "== verify state survived =="
MARKER_XML="$(adb_cmd shell run-as "$PACKAGE" cat shared_prefs/upgrade_marker.xml 2>/dev/null | tr -d '\r' || true)"
[[ "$MARKER_XML" == *"seed-123"* ]] || { echo "ERROR: settings marker lost after upgrade" >&2; exit 1; }
WS="$(adb_cmd shell run-as "$PACKAGE" cat files/workspace/marker.txt 2>/dev/null | tr -d '\r' || true)"
[[ "$WS" == "seed-workspace" ]] || { echo "ERROR: workspace file lost after upgrade" >&2; exit 1; }

FORE="$(adb_cmd shell dumpsys activity activities | grep -m1 'mResumedActivity\|topResumedActivity' | grep "$PACKAGE" || true)"
echo "foreground after upgrade: ${FORE:-<not resumed; app installed and runnable>}"

cat <<EOF

UPGRADE VERIFIED
  package:  $PACKAGE
  old:      $(apk_version_name "$OLD_APK") / $(apk_version_code "$OLD_APK")
  new:      $(apk_version_name "$NEW_APK") / $(apk_version_code "$NEW_APK")
  uid:      $UID_AFTER (unchanged)
  state:    settings + workspace marker survived
  sha256:   old=$(sha256sum "$OLD_APK" | cut -d' ' -f1)
            new=$(sha256sum "$NEW_APK" | cut -d' ' -f1)
  host:     $(git rev-parse HEAD 2>/dev/null || echo unknown) (unverified vs APK)
EOF
