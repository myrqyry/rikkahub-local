# P0 build and data safety implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RikkaHub Local safe to build, install in place, back up, restore,
and upgrade before runtime stabilization work proceeds.

**Architecture:** Keep production data handling inside the existing app export
path. Add a host-side debug-only `adb` utility that streams private files through
`run-as`, validates sensitive archives, and never performs destructive operations
implicitly. Keep Room migration tests on Android instrumentation, settings
rewrite tests on the JVM, and APK upgrade verification in a host-side harness.

**Tech Stack:** Kotlin, Room, Android instrumentation, JUnit, Gradle Kotlin DSL,
GitHub Actions, POSIX shell, `adb`, `tar`, `sha256sum`, and Python 3 standard
library only where shell portability requires archive validation.

## Global Constraints

- Preserve installed application data during stabilization and upgrade testing.
- Never uninstall, run `pm clear`, delete app data, reset databases, or replace persistent storage unless the user explicitly authorizes a narrowly scoped destructive test on a disposable installation.
- Preserve application ID `excp.rikkahub.local`.
- Development APK installation must use `adb install -r`.
- `run-as` is valid only for debuggable builds; fail clearly when `adb exec-out run-as <package> id` fails.
- Stream private files through `adb exec-out run-as`; do not use `adb pull` for app-private paths.
- The on-device Room files are `databases/rikka_hub`, `databases/rikka_hub-wal`, and `databases/rikka_hub-shm`.
- Do not add destructive Room migration fallback.
- Do not add mocks or placeholders to satisfy acceptance criteria.
- Every task ends with a focused test or validation command and a separate commit.

---

## File map

The implementation uses these focused boundaries:

- `.github/workflows/android.yml`: pull-request and `master` build/test jobs.
- `.gitignore`: sensitive local backup destination pattern.
- `AGENTS.md`: root preservation rules and reporting contract.
- `app/AGENTS.md`: app-specific migration and upgrade safety rules.
- `scripts/rikkahub-data.sh`: debug-only backup, restore, and metadata command.
- `scripts/README.md`: exact safe usage and destructive-operation warnings.
- `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30.kt`: schema-29→30 SQL.
- `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/MigrationChainTest.kt`: 27/28/29/30→31 chain tests.
- `app/src/androidTest/java/me/rerere/rikkahub/data/db/ImportedDatabaseReconcilerTest.kt`: schema-31 import validation.
- `app/src/test/java/me/rerere/rikkahub/data/datastore/migration/SettingsJsonMigratorPreservationTest.kt`: settings fixture preservation.
- `app/src/androidTest/java/me/rerere/rikkahub/data/preferences/SettingsPersistenceInstrumentedTest.kt`: one Android settings check.
- `scripts/rikkahub-upgrade.sh`: in-place old-APK→new-APK harness.
- `app/src/androidTest/java/me/rerere/rikkahub/upgrade/RealUpgradeStateContractTest.kt`: state contract shared by upgrade verification.

## Task 1: Add preservation rules and CI baseline

**Files:**
- Modify: `AGENTS.md`
- Modify: `app/AGENTS.md`
- Create: `.github/workflows/android.yml`
- Modify: `.gitignore`

**Interfaces:**
- Produces CI jobs named `unit-lint-build` and `android-migrations`.
- Produces the repository rule that all deployment commands preserve installed data.

- [ ] **Step 1: Add the explicit preservation rule.**

Add this exact section to the root instructions:

```markdown
## Installed data preservation

During stabilization and upgrade testing, preserve installed app data. Never
uninstall the app, run `pm clear`, delete its data directory, change
`applicationId`, reset a database, or replace persistent storage unless the user
explicitly authorizes a narrowly scoped destructive test on a disposable
installation. Install development APKs with `adb install -r`. Before any
migration or storage operation, create a verified backup when the workflow
supports it.

Implementation reports must list completed work, remaining work, tests executed,
tests not executed and why, device verification, known risks, and the commit or
PR containing the work.
```

- [ ] **Step 2: Add the app-module warning.**

Extend `app/AGENTS.md` with the database filename, generated-schema warning, and
the rule that migration tests must use the registered production chain.

- [ ] **Step 3: Ignore sensitive backup output.**

Add `/var/tmp/rikkahub-local-backups/` and `.rikkahub-backup/` to `.gitignore`.
Do not add a repository-local default backup directory.

- [ ] **Step 4: Create the CI workflow.**

Create `.github/workflows/android.yml` with `pull_request` and `push` triggers
for `master`. Use `actions/checkout` with `submodules: recursive`,
Run each command as an independent step:

```yaml
- run: ./gradlew test --no-daemon
- run: ./gradlew lint --no-daemon
- run: ./gradlew assembleDebug --no-daemon
```

Upload `**/build/test-results/**/*.xml`, `**/build/reports`, and
`app/build/outputs/apk/debug/*.apk` with `if-no-files-found: warn`; report/lint
uploads must use `if: failure()` where appropriate. Make all three verification
steps required for the job to pass.

- [ ] **Step 5: Validate the baseline.**

Run:

```bash
git diff --check
./gradlew test --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon
```

Expected: all commands exit 0. Commit:
`ci: establish Android stabilization baseline`.

## Task 2: Add the debug-only data backup utility

**Files:**
- Create: `scripts/rikkahub-data.sh`
- Create: `scripts/README.md`

**Interfaces:**
- `scripts/rikkahub-data.sh check --serial SERIAL --package PACKAGE`
- `scripts/rikkahub-data.sh backup --serial SERIAL --package PACKAGE [--output PATH]`
- `scripts/rikkahub-data.sh restore --serial SERIAL --package PACKAGE --input PATH --confirm`
- `scripts/rikkahub-data.sh metadata --serial SERIAL --package PACKAGE --apk PATH`

- [ ] **Step 1: Write shell-level validation cases.**

Create a testable command structure where `check` calls:

```bash
adb -s "$serial" exec-out run-as "$package" id
```

and exits nonzero with the message `run-as unavailable: package must be a
debuggable build` when the command fails. Test the missing argument, missing
serial, missing package, missing input archive, absent `--confirm`, and
nonexistent APK cases with shell assertions.

- [ ] **Step 2: Implement metadata collection.**

Collect package ID, `versionName`, `versionCode`, device serial, model, Android
release, UTC timestamp, `git rev-parse HEAD` labeled `host_commit_unverified`,
and `sha256sum` of the supplied installed APK. Store the metadata as an archive
entry and never infer APK commit identity from the host checkout.

- [ ] **Step 3: Implement streamed backup.**

Stop the package only if it was running, then stream a tar archive from the app
private root through `adb exec-out run-as "$package" tar -cf - ...`. Include:

```text
databases/rikka_hub
databases/rikka_hub-wal
databases/rikka_hub-shm
shared_prefs/
files/datastore/
files/uploads/
files/skills/
files/fonts/
```

Exclude model weights, cache directories, browser profiles, and generated
temporary files. Add `manifest.json` with included/excluded paths and SHA-256
checksums. Write the host archive with `umask 077` and `chmod 600`. Restart the
package only when it was running before the backup.

- [ ] **Step 4: Implement archive validation.**

Reject archives with absolute paths, `..` path components, symlink entries, a
missing manifest, package mismatch, checksum mismatch, or an entry outside the
declared include list. Use `tar --list --file` and a small POSIX/Python standard
library validator; do not extract before validation succeeds.

- [ ] **Step 5: Implement restore as the only destructive command.**

Require the literal `restore` subcommand and `--confirm`. Validate the archive,
verify package identity, make a rescue backup, capture running state, stop the
app, remove `databases/rikka_hub-wal` and `databases/rikka_hub-shm`, and stream
validated files into the app through `run-as`. Map backup entry `rikka_hub.db`
to the on-device filename `databases/rikka_hub`. Leave the app stopped unless it
was running before restore.

- [ ] **Step 6: Document sensitive handling.**

`scripts/README.md` must label archives sensitive because they may contain API
keys, state that the default destination is outside the repository, show safe
backup and restore commands, and explicitly state that the script never
uninstalls or clears data.

- [ ] **Step 7: Verify without touching the primary app data.**

Run `check` and `metadata` against the connected debug device. Create a backup,
inspect its manifest and mode, and run restore only against a disposable debug
package/profile explicitly created for this test. Commit:
`feat: add protected debug data backup tooling`.

## Task 3: Repair and test the Room migration chain

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30.kt`
- Modify: `app/src/androidTest/java/me/rerere/rikkahub/data/db/ImportedDatabaseReconcilerTest.kt`
- Create: `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/MigrationChainTest.kt`
- Modify: `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30_Test.kt`

**Interfaces:**
- Each test starts from an exported schema version and opens the final schema through `AppDatabase`.
- The registered chain is exactly `Migration_27_28`, `Migration_28_29`, `Migration_29_30`, and `Migration_30_31` for versions 27 through 31.

- [ ] **Step 1: Add a failing schema-chain test.**

For each start version in `listOf(27, 28, 29, 30)`, create the database with
`MigrationTestHelper.createDatabase`, seed one representative row in every
available table, close it, run all registered migrations to version 31, and
call `runMigrationsAndValidate`. Assert that `conversationentity.revision` is
zero for migrated rows and that `agent_run_events` has exactly one unique
`(run_id, sequence)` index.

- [ ] **Step 2: Repair migration SQL only where the schema proves it wrong.**

Use the generated Room schema table names, not entity class names. Preserve the
existing `agent_run_events` foreign key and indexes; do not add fallback table
creation or destructive recovery.

- [ ] **Step 3: Repair reconciliation coverage.**

Register `Migration_29_30` and `Migration_30_31` in
tables, schema-30 tables, and schema-31 tables after reopening through the real
builder.

- [ ] **Step 4: Run instrumentation.**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.data.db.migrations.MigrationChainTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.data.db.ImportedDatabaseReconcilerTest
```

Expected: both commands pass on the managed Android test device. Commit:
`test: cover complete Room upgrade chains`.

## Task 4: Add settings and DataStore preservation tests

**Files:**
- Inspect/modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/SettingsJsonMigrator.kt`
- Create/modify: `app/src/test/java/me/rerere/rikkahub/data/datastore/migration/SettingsJsonMigratorPreservationTest.kt`
- Create: `app/src/androidTest/java/me/rerere/rikkahub/data/preferences/SettingsPersistenceInstrumentedTest.kt`

**Interfaces:**
- JVM tests call the existing pure migration helper without Android storage.
- The Android test writes representative preferences, kills and reopens the real store, and verifies values.

- [ ] **Step 1: Add fixture cases before changing production code.**

Cover assistants, providers, intentionally backed-up credentials, global model
roles, assistant overrides, local/cloud privacy settings, selected TTS and ASR
providers, and unknown future fields. Assert that unknown fields are ignored or
rejected according to the existing migrator contract, not silently discarded by
new test logic.

- [ ] **Step 2: Add the Android integration check.**

Use a unique test preference/store name, write values through the actual DataStore
or SharedPreferences abstraction, close the store, reopen it, and assert every
representative value survives. Do not touch the production `rikka_hub` database
or primary device profile.

- [ ] **Step 3: Run both test classes.**

Run `./gradlew :app:testDebugUnitTest --tests '*SettingsJsonMigratorPreservationTest'`
and the targeted connected instrumentation class. Commit:
`test: verify settings survive migration and reopen`.

## Task 5: Add the in-place APK upgrade harness

**Files:**
- Create: `scripts/rikkahub-upgrade.sh`
- Create: `app/src/androidTest/java/me/rerere/rikkahub/upgrade/RealUpgradeStateContractTest.kt`
- Modify: `scripts/README.md`

**Interfaces:**
- `scripts/rikkahub-upgrade.sh --serial SERIAL --old OLD_APK --new NEW_APK --package excp.rikkahub.local --backup PATH`
- Harness operations are `install old`, seed/verify state, `adb install -r new`, launch, verify state; there is no uninstall path.

- [ ] **Step 1: Add argument and identity checks.**

Read package IDs and signing certificate fingerprints from both APKs using
`apkanalyzer` or `aapt`; refuse mismatches before installation. Require an
existing backup path and verify it before touching the device.

- [ ] **Step 2: Add in-place installation only.**

Install with:

```bash
adb -s "$serial" install -r "$old_apk"
adb -s "$serial" install -r "$new_apk"
```

Never call `adb uninstall`, `pm clear`, or `cmd package clear`. Verify the
package remains installed and the package UID is unchanged after the second
install.

- [ ] **Step 3: Add state contract verification.**

Use a debug-only test contract that records a conversation, settings values,
workspace file, scheduled job, and generated-media reference before upgrade,
then verifies the same values after launch and after process restart. Keep the
contract independent of the test APK replacement mechanism.

- [ ] **Step 4: Run only on a disposable upgrade profile or explicitly backed-up primary.**

Record package ID, version name/code, APK SHA-256 values, host commit labels,
device model, Android version, serial, and timestamp. Commit:
`test: verify in-place APK upgrades preserve state`.

## Task 6: Add the managed Android-test CI lane and clean-clone record

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `docs/references/p0-clean-clone-verification.md`

**Interfaces:**
- CI exposes an `android-migrations` result that is required by the release gate.
- The clean-clone record is a repeatable procedure, not a claim that a local checkout is clean.

- [ ] **Step 1: Configure the Android test lane.**

Use the repository’s supported managed-device or emulator action. Run only the
targeted migration and reconciliation classes first to keep runtime bounded.
Upload connected-test reports on failure. The job must fail when any targeted
instrumentation test fails.

- [ ] **Step 2: Document clean-clone verification.**

Document `git clone --recurse-submodules`, native submodule checks, JDK 17,
`./gradlew test --no-daemon`, `./gradlew lint --no-daemon`,
`./gradlew assembleDebug --no-daemon`, in-place install, launch verification,
and the metadata record required by the release gate.

- [ ] **Step 3: Run the complete P0 safety gate.**

Run:

```bash
./gradlew test --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon
./gradlew :app:connectedDebugAndroidTest
```

Run the backup `check`, backup, and upgrade harness against the designated
debug/disposable profile. Do not uninstall or clear the primary installation.
Commit: `ci: require Android migration verification`.

## Completion gate

This plan is complete only when all of the following are evidenced:

- CI is present and green for unit tests, lint, assemble, and targeted Android tests.
- The clean-clone procedure produced a launchable APK.
- Backup and restore archives validate, remain outside the repository, and preserve ownership and WAL/SHM handling.
- Room chains 27/28/29/30→31 pass and reconciliation opens at schema 31.
- Settings and DataStore preservation tests pass.
- In-place APK upgrade passes with the same package ID and signing key.
- The primary phone was never uninstalled, cleared, or reset.

After this gate, create separate plans for TTS/audio, ordinary chat, FunctionGemma,
agent/tool/workspace routing, and user-visible feature surfaces.
