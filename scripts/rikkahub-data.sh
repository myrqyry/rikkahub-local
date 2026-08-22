#!/usr/bin/env bash
#
# rikkahub-data.sh — debug-only backup / restore for RikkaHub Local.
#
# Streams app-private data through `adb exec-out run-as`. The utility NEVER
# uninstalls the app, never runs `pm clear`, and never deletes app data. The only
# command that writes into the app is `restore`, and it requires explicit
# confirmation after a rescue backup has been made.
#
# `run-as` only works on debuggable builds. For a release installation, use the
# app's own in-app export instead.
#
set -euo pipefail

PACKAGE="${RIKKAHUB_PACKAGE:-excp.rikkahub.local}"
UMASK=077
BACKUP_DIR="${RIKKAHUB_BACKUP_DIR:-/var/tmp/rikkahub-local-backups}"

# On-device Room database files (no `.db` suffix on device).
DB_MAIN="databases/rikka_hub"
DB_WAL="databases/rikka_hub-wal"
DB_SHM="databases/rikka_hub-shm"

# Private paths included in a backup.
INCLUDE_PATHS=(
  "databases/rikka_hub"
  "databases/rikka_hub-wal"
  "databases/rikka_hub-shm"
  "shared_prefs"
  "files/datastore"
  "files/upload"
  "files/skills"
  "files/fonts"
)

# Private paths intentionally excluded (weights, caches, browser profiles, temp).
EXCLUDE_PATHS=(
  "files/models"
  "files/cache"
  "files/browser-profile"
  "files/tmp"
  "cache"
  "code_cache"
  "no_backup"
)

INCLUDE_JSON="$(python3 -c 'import json,sys;print(json.dumps(sys.argv[1:]))' "${INCLUDE_PATHS[@]}")"
EXCLUDE_JSON="$(python3 -c 'import json,sys;print(json.dumps(sys.argv[1:]))' "${EXCLUDE_PATHS[@]}")"

log() { printf '[rikkahub-data] %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat >&2 <<'EOF'
usage: rikkahub-data.sh <subcommand> [options]

subcommands:
  check    --serial SERIAL [--package PKG]
           Verify run-as (debuggable) access to the app's private data.

  backup   --serial SERIAL [--package PKG] [--output PATH]
           Stream the app's private data into a checksummed, 0600 tar archive.
           Default output: /var/tmp/rikkahub-local-backups/<pkg>-<timestamp>.tar.gz

  restore  --serial SERIAL --input PATH --confirm [--package PKG]
           Validate the archive, make a rescue backup, then restore the app data
           in place. Requires the literal `restore` subcommand AND --confirm.

  metadata --serial SERIAL [--package PKG] [--apk PATH]
           Print installed-app + host metadata as JSON (no app data touched).
EOF
  exit 1
}

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"; }
require_arg() { [[ -n "${2:-}" ]] || die "$1 requires an argument: $2"; }

adb_cmd() { adb -s "$serial" "$@"; }

# ---------------------------------------------------------------------------
# run-as probe
# ---------------------------------------------------------------------------
probe_run_as() {
  local out
  out="$(adb_cmd exec-out run-as "$package" id 2>&1 || true)"
  if [[ "$out" != *"uid="* ]]; then
    die "run-as unavailable: package must be a debuggable build (got: $out)"
  fi
}

# ---------------------------------------------------------------------------
# metadata
# ---------------------------------------------------------------------------
collect_metadata() {
  local apk="${1:-}" ts now model release
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  model="$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
  release="$(adb_cmd shell getprop ro.build.version.release | tr -d '\r')"
  local vn vc
  vn="$(adb_cmd shell dumpsys package "$package" | grep -m1 versionName | sed 's/.*versionName=//;s/\r//' || true)"
  vc="$(adb_cmd shell dumpsys package "$package" | grep -m1 versionCode | sed 's/.*versionCode=//;s/ .*//;s/\r//' || true)"
  local apksha=""
  if [[ -n "$apk" ]]; then
    [[ -f "$apk" ]] || die "apk not found: $apk"
    apksha="$(sha256sum "$apk" | cut -d' ' -f1)"
  fi
  python3 - <<EOF
import json
print(json.dumps({
  "package_id": "$package",
  "version_name": "$vn",
  "version_code": "$vc",
  "installed_apk_sha256": "$apksha",
  "host_commit_unverified": "$(git rev-parse HEAD 2>/dev/null || echo unknown)",
  "device_model": "$model",
  "android_release": "$release",
  "device_serial": "$serial",
  "created_at_utc": "$ts",
  "include_paths": $INCLUDE_JSON,
  "exclude_paths": $EXCLUDE_JSON,
}, indent=2))
EOF
}

# ---------------------------------------------------------------------------
# backup
# ---------------------------------------------------------------------------
do_backup() {
  local output="${1:-}" now ts pid running=no
  require_cmd adb require_cmd tar require_cmd sha256sum
  probe_run_as

  now="$(date -u +%Y%m%dT%H%M%SZ)"
  output="${output:-$BACKUP_DIR/$package-$now.tar}"
  mkdir -p "$(dirname "$output")"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  chmod 700 "$TMP_DIR"

  pid="$(adb_cmd shell pidof "$package" | tr -d '\r' || true)"
  if [[ -n "$pid" ]]; then
    running=yes
    adb_cmd shell am force-stop "$package" >/dev/null
    sleep 1
  fi

  local content="$TMP_DIR/contents"
  mkdir -p "$content"
  # Stream private files out; empty dirs are tolerated.
  adb_cmd exec-out run-as "$package" tar -cf - "${INCLUDE_PATHS[@]}" > "$TMP_DIR/raw.tar" || true

  tar -xf "$TMP_DIR/raw.tar" -C "$content" 2>/dev/null || true
  if [[ -z "$(find "$content" -type f | head -1)" ]]; then
    log "no backup content present (empty or fresh install)"
  fi

  # Checksums per file, written as an ordered manifest.
  local manifest="$content/manifest.json"
  python3 - "$content" "$INCLUDE_JSON" "$EXCLUDE_JSON" <<'EOF'
import hashlib, json, os, sys
root, inc, exc = sys.argv[1], sys.argv[2], sys.argv[3]
inc = json.loads(inc); exc = json.loads(exc)
entries = []
for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = [d for d in dirnames if d not in ("cache", "code_cache")]
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        rel = os.path.relpath(p, root)
        if rel == "manifest.json":
            continue
        h = hashlib.sha256(open(p, "rb").read()).hexdigest()
        entries.append({"path": rel, "sha256": h})
entries.sort(key=lambda e: e["path"])
meta = {
  "manifest_version": 1,
  "include_paths": inc,
  "exclude_paths": exc,
  "files": entries,
}
json.dump(meta, open(os.path.join(root, "manifest.json"), "w"), indent=2)
EOF

  local meta_json
  meta_json="$(collect_metadata)"
  python3 - "$content" "$meta_json" <<'EOF'
import json, os, sys
content, meta = sys.argv[1], sys.argv[2]
json.dump(json.loads(meta), open(os.path.join(content, "metadata.json"), "w"), indent=2)
EOF

  (cd "$TMP_DIR" && tar -czf "$output" contents)

  # Restart only if it was running before.
  if [[ "$running" == yes ]]; then
    adb_cmd shell monkey -p "$package" 1 >/dev/null 2>&1 || true
  fi

  chmod 600 "$output"
  log "backup written: $output"
}

# ---------------------------------------------------------------------------
# validation
# ---------------------------------------------------------------------------
validate_archive() {
  local archive="$1"
  [[ -f "$archive" ]] || die "archive not found: $archive"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  chmod 700 "$TMP_DIR"

  # Safe-path and symlink scan on the tar listing before any extraction.
  tar -tzf "$archive" | python3 -c '
import sys
for line in sys.stdin:
    p = line.rstrip("\n").rstrip("/")
    if p == "contents" or p == "contents/manifest.json" or p == "contents/metadata.json":
        continue
    if not p.startswith("contents/"):
        sys.exit(f"unsafe entry outside contents/: {p}")
    rel = p[len("contents/"):]
    if rel.startswith("/") or ".." in rel.split("/"):
        sys.exit(f"unsafe path: {p}")
' || die "archive rejected: unsafe path"

  local has_symlink
  has_symlink="$(tar -tvzf "$archive" | awk '$1 ~ /^l/ {print $6}' | head -1 || true)"
  [[ -z "$has_symlink" ]] || die "archive rejected: symlink entry: $has_symlink"

  # Full extraction, then verify metadata + checksums against the extracted tree.
  tar -xzf "$archive" -C "$TMP_DIR"

  # Package identity must match.
  local pkg_in_meta
  pkg_in_meta="$(python3 -c "import json;print(json.load(open('$TMP_DIR/contents/metadata.json'))['package_id'])")"
  [[ "$pkg_in_meta" == "$package" ]] || die "archive package mismatch: $pkg_in_meta != $package"

  # Manifest must declare every extracted file, with matching hashes, and every declared
  # file must actually be present.
  python3 - "$TMP_DIR/contents" <<'EOF'
import hashlib, json, os, sys
root = sys.argv[1]
manifest = json.load(open(os.path.join(root, "manifest.json")))
declared = {f["path"]: f["sha256"] for f in manifest["files"]}
if not declared:
    sys.exit("archive rejected: empty manifest")
missing = []
for p, want in declared.items():
    fp = os.path.join(root, p)
    if not os.path.isfile(fp):
        missing.append(p)
        continue
    got = hashlib.sha256(open(fp, "rb").read()).hexdigest()
    if got != want:
        sys.exit(f"archive rejected: checksum mismatch for {p}")
if missing:
    sys.exit("archive rejected: missing entries: " + ", ".join(missing[:20]))
undelclared = []
for dirpath, _dirnames, filenames in os.walk(root):
    for fn in filenames:
        p = os.path.relpath(os.path.join(dirpath, fn), root)
        if p in ("manifest.json", "metadata.json"):
            continue
        if p not in declared:
            undelclared.append(p)
if undelclared:
    sys.exit("archive rejected: undeclared files: " + ", ".join(undelclared[:10]))
print("archive OK: %d entries" % len(declared))
EOF
}

# ---------------------------------------------------------------------------
# restore
# ---------------------------------------------------------------------------
do_restore() {
  local archive="$1" pid running=no
  require_cmd adb require_cmd tar
  # validate_archive fully extracts and verifies into $TMP_DIR/contents.
  validate_archive "$archive"
  local extracted="$TMP_DIR"

  # Rescue backup before touching anything.
  log "creating rescue backup before restore"
  do_backup "$BACKUP_DIR/$package-rescue-$(date -u +%Y%m%dT%H%M%SZ).tar"

  pid="$(adb_cmd shell pidof "$package" | tr -d '\r' || true)"
  if [[ -n "$pid" ]]; then
    running=yes
    adb_cmd shell am force-stop "$package" >/dev/null
    sleep 1
  fi

  # Remove stale WAL/SHM, then restore the DB set together. Stream host -> device via exec-in.
  # The stream must NOT contain a "." root entry: extracting it would reset the package data
  # dir mode (e.g. to 0775 under the app umask), which blocks run-as afterwards. Subdirectory
  # modes are irrelevant to run-as; only the root data dir is checked.
  adb_cmd shell run-as "$package" rm -f "$DB_WAL" "$DB_SHM" || true
  (cd "$extracted/contents" && find . -mindepth 1 -print0 | tar --null --no-recursion -cf - -T -) |
    adb_cmd exec-in run-as "$package" tar -xf -

  if [[ "$running" == yes ]]; then
    adb_cmd shell monkey -p "$package" 1 >/dev/null 2>&1 || true
  fi
  log "restore complete"
}

# ---------------------------------------------------------------------------
# dispatch
# ---------------------------------------------------------------------------
cmd="${1:-}"; shift || true
serial="" output="" input="" apk="" confirm=no

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --package) PACKAGE="${2:-}"; shift 2 ;;
    --output) output="${2:-}"; shift 2 ;;
    --input) input="${2:-}"; shift 2 ;;
    --apk) apk="${2:-}"; shift 2 ;;
    --confirm) confirm=yes; shift ;;
    *) usage ;;
  esac
done
package="$PACKAGE"

[[ -n "$serial" ]] || die "missing --serial"
case "$cmd" in
  check)
    probe_run_as
    log "run-as ok: $package"
    ;;
  backup)
    do_backup "$output"
    ;;
  restore)
    [[ "$confirm" == yes ]] || die "restore requires --confirm"
    [[ -n "$input" ]] || die "restore requires --input PATH"
    do_restore "$input"
    ;;
  metadata)
    collect_metadata "$apk"
    ;;
  *)
    usage
    ;;
esac
