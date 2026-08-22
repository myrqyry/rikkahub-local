# RikkaHub Local developer data tooling

## `rikkahub-data.sh` — protected debug backup / restore

`run-as` access works **only for debuggable builds**. For a normal release
installation, use the app's in-app export instead.

The tool **never** uninstalls the app, runs `pm clear`, or deletes app data. The
only command that writes into the app is `restore`, which requires the literal
`restore` subcommand plus `--confirm`, validates the archive first, and makes a
rescue backup before touching anything.

Backup archives are **sensitive**: preferences and DataStore files may contain
API keys and credentials. Archives are written with host permissions `0600`, the
default destination is outside the repository
(`/var/tmp/rikkahub-local-backups/`), and the directory pattern is git-ignored.
Do not commit or share these archives.

### Commands

```bash
# Verify debug access to the installed app
scripts/rikkahub-data.sh check --serial <SERIAL> [--package excp.rikkahub.local]

# Back up the app's private data (databases, prefs, datastore, uploads, skills, fonts)
scripts/rikkahub-data.sh backup --serial <SERIAL> [--output /path/to/archive.tar]

# Print installed-app + host metadata as JSON (no app data touched)
scripts/rikkahub-data.sh metadata --serial <SERIAL> [--apk path/to.apk]

# Restore from a validated archive (destructive to current app data; rescue backup made first)
scripts/rikkahub-data.sh restore --serial <SERIAL> --input /path/to/archive.tar --confirm
```

### What is included / excluded

Included: `databases/rikka_hub`, `databases/rikka_hub-wal`, `databases/rikka_hub-shm`,
`shared_prefs/`, `files/datastore/`, `files/uploads/`, `files/skills/`, `files/fonts/`.

Excluded: local model weights, caches, browser profiles, and generated temporary
files. The `manifest.json` inside each archive records the include/exclude lists
and a SHA-256 checksum per restored file.

### Restore safety

Restore stops the app, removes stale `-wal`/`-shm` files, and restores the
database set together through `run-as` so app-user ownership is preserved. The
app is left stopped unless it was running before the restore.

## `test-rikkahub-data.sh` — shell validation

Runs the CLI argument and `run-as` failure-path cases against a test-double `adb`
in `PATH` (no device required).

```bash
scripts/test-rikkahub-data.sh
```

## `rikkahub-upgrade.sh` — in-place APK upgrade harness

Verifies a genuine `adb install -r` upgrade preserves app state. Checks package
ID and signing certificate identity, makes a safety backup, seeds state markers,
installs old in place, installs new in place, and verifies the package UID is
unchanged and the seeded state survived.

```bash
scripts/rikkahub-upgrade.sh --serial <SERIAL> --old <old.apk> --new <new.apk> \
  --package excp.rikkahub.local --backup /var/tmp/rikkahub-local-backups
```

Old and new APKs must share the exact application ID and signing key. The harness
never uninstalls and never runs `pm clear`.

> [!IMPORTANT]
> Never run `./gradlew :app:connectedDebugAndroidTest` against your primary phone:
> AGP uninstalls the target app package after the instrumentation run, wiping its
> data. Run connected tests only on a disposable emulator or disposable device.
> The upgrade harness is the safe way to test in-place upgrades on a real device.
