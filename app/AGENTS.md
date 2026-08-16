# App — Android Application Module

## What Lives Here

Main Android app module: UI screens, navigation, Compose components, service layer, and the Android entry point.

## Key Files

| File | Purpose |
|------|---------|
| `src/main/AndroidManifest.xml` | App manifest, permissions, activities |
| `src/main/java/me/rerere/rikkahub/` | Main app code |
| `build.gradle.kts` | App-level dependencies, plugins |

## Deviations from Root

- No deviations — follow root conventions.

## Watch Out For

- `proguard-rules.pro` — keep ProGuard rules in sync when adding new reflection-based libs
- `schemas/` — generated Room database schemas, do not edit manually
- The on-device Room database files are `databases/rikka_hub`, `databases/rikka_hub-wal`,
  and `databases/rikka_hub-shm` (no `.db` suffix on device; that name is only a backup
  archive convention). Migration tests must use the exact production chain registered in
  `di/DataSourceModule.kt` and the generated schema table names, never entity class names.
- Preserve installed app data: never uninstall, run `pm clear`, delete the data directory,
  change `applicationId`, or reset a database during stabilization/upgrade testing unless
  the user explicitly authorizes a destructive test on a disposable installation.