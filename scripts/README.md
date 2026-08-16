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

Runs the CLI argument and `run-as` failure-path cases against a fake `adb` in
`PATH` (no device required).

```bash
scripts/test-rikkahub-data.sh
```
