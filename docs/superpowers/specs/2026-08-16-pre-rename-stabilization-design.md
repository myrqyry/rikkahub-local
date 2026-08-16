# RikkaHub Local pre-rename stabilization design

This design establishes a trustworthy baseline before the application receives
its final name. It prioritizes preservation of installed user state, verified
database upgrades, reproducible builds, and explicit reporting over feature
delivery.

## Operating rules

During stabilization and upgrade testing:

- Freeze nonessential feature development.
- Permit bug fixes, tests, diagnostics, lifecycle repairs, and documentation.
- Preserve application ID `excp.rikkahub.local`.
- Never uninstall, run `pm clear`, delete app data, reset databases, or replace
  persistent storage unless the user explicitly authorizes a narrowly scoped
  destructive test on a disposable installation.
- Install development APKs in place with `adb install -r`.
- Do not add mocks or placeholders to satisfy acceptance criteria.
- Do not add destructive Room migration fallback.
- Do not claim the complete plan is finished when only a phase is complete.

Every implementation report must state completed work, remaining work, tests
executed, tests not executed and why, device verification, known risks, and the
commit or PR containing the work.

## Build and CI

Add `.github/workflows/android.yml` for pull requests and pushes to `master`.
The workflow checks out recursive submodules, installs JDK 17, enables Gradle
caching, and runs the following commands without a daemon:

```text
./gradlew test --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon
```

The workflow uploads test and lint reports on failure and uploads the debug APK
as an artifact. Add a separate Android-test lane for targeted Room migration and
reconciliation tests. This lane may run manually or nightly initially, but the
release gate must require its result.

Add a clean-clone verification procedure that records the recursive clone,
native submodule population, commit SHA, device, Android version, and APK used.
The resulting APK must install and launch on a physical device without copying
local build artifacts or caches.

## Data preservation and backup tool

Add a host-side `scripts/rikkahub-data.sh` command for debuggable installations.
The script must test `adb exec-out run-as "$PACKAGE" id` before any operation and
fail clearly when the package is not debuggable. It must not pretend to support a
normal release installation; users must use the existing in-app export for that
case.

The default backup destination is outside the repository, has host permissions
`0600`, and is identified as sensitive. Add its pattern to `.gitignore`.

Backup uses one stopped-app tar stream through `adb exec-out run-as`, not
`adb pull`, and records exact installed metadata:

- Package ID.
- Version name and version code.
- Installed APK SHA-256.
- Host checkout commit, explicitly labeled unverified against the APK.
- Device model, Android version, serial, and timestamp.
- Included and excluded paths.

The database entries use the actual on-device names:

```text
databases/rikka_hub
databases/rikka_hub-wal
databases/rikka_hub-shm
```

The archive convention may call the primary entry `rikka_hub.db`, but the
restore mapping must use the real on-device filename. Backups include the Room
database, preferences, DataStore files, uploads, skills, and fonts. They exclude
local model weights, caches, browser profiles, and generated temporary files.
The archive records checksums and path lists.

Restore requires an explicit `restore` subcommand and confirmation flag. It must:

1. Validate archive checksums and reject absolute or traversal paths.
2. Refuse package-ID mismatches.
3. Create a rescue backup before overwriting installed data.
4. Record whether the app was running, then stop it.
5. Remove existing `rikka_hub-wal` and `rikka_hub-shm` before restoring the
   database set together.
6. Write through `run-as` so app-user ownership is preserved.
7. Leave the app stopped unless it was running before the operation.

No build or deployment command may call uninstall, `pm clear`, or equivalent
data deletion.

## Database and settings verification

Room migration and reconciliation tests run as Android instrumentation tests.
Add complete chain coverage for `27 -> 31`, `28 -> 31`, `29 -> 31`, and
`30 -> 31`. Each test creates the starting schema from exported Room schemas,
seeds representative data, executes the registered chain, validates schema 31,
reopens through the real `AppDatabase` builder, and checks rows, defaults,
foreign keys, and indexes.

Coverage includes conversations, message trees, memories, generated media,
workspaces, folders, scheduled jobs, workflows, RAG vectors, AgentRun records
and events, conversation revisions, and zero procedures. Repair
`ImportedDatabaseReconcilerTest` so it registers `Migration_29_30` and
`Migration_30_31`, validates schema 31, and documents version 29 as an
intermediate reconciliation target when applicable.

Settings and provider state are tested separately from Room. Fast JVM fixture
tests exercise `SettingsJsonMigrator` for assistants, providers and intentionally
backed-up credentials, model assignments, privacy settings, TTS/ASR selections,
and unknown future fields. One Android integration test verifies the actual
DataStore plumbing.

Add a genuine in-place APK upgrade harness. It installs an older compatible APK,
seeds and verifies state, installs the new APK with `adb install -r`, launches,
and verifies state again. Both APKs must use the exact application ID and signing
key. The harness must save a safety backup before testing against a primary
installation and must never uninstall.

## Runtime stabilization order

After build, data, and upgrade gates are green, stabilize runtime behavior in
this order:

1. Isolate and harden TTS and audio resource ownership, including idempotent
   release, cancellation, provider switching, backgrounding, navigation, and
   repeated sessions. Test ASR and voice notes separately.
2. Verify ordinary cloud and LiteRT chat through streaming, stopping,
   regeneration, editing, switching, process death, context trimming, model
   failure, and privacy resolution paths.
3. Disable automatic LiteRT tool execution and route structured FunctionGemma
   calls through the existing approval system. Expose only the seven trained
   actions and verify each on-device.
4. Validate AgentRun receipts, workspace/terminal production routing, advanced
   procedures, workflows, scheduled jobs, Termux, SSH, and cancellation.
5. Verify multimodal, sharing, skills/plugins, and Stable Diffusion behavior;
   hide or label unfinished capabilities as Experimental.

## Release and rename gate

The release candidate requires a clean recursive clone, release configuration,
unit tests, lint, migration instrumentation, targeted device tests, in-place
installation over an existing app, state verification, backup verification, and
normal use across cloud chat, local chat, attachments, TTS, FunctionGemma,
workspace/terminal, sharing, lifecycle events, and reboot.

Only after those gates pass may the product name, icon, strings, diagnostics,
README, screenshots, and website change. The application ID remains
`excp.rikkahub.local`, and the renamed build must repeat CI, migration, backup,
upgrade, and smoke gates.
