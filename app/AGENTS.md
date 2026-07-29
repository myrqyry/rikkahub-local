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