# P0 clean-clone verification

This is the repeatable procedure used to prove a clean recursive clone of
RikkaHub Local builds and launches on a physical device. It is a **procedure**,
not a claim about any particular local checkout: run it from a fresh clone.

## Prerequisites

- A Linux or macOS host with JDK 17, the Android SDK (with `adb` on `PATH`), and
  a physical Android device (or disposable emulator) connected.
- Network access to GitHub for the submodules.

## Steps

```bash
# 1. Clean recursive clone (never reuse local build artifacts or caches)
git clone --recurse-submodules <repo-url> rikkahub-local-clean
cd rikkahub-local-clean

# 2. Confirm both native submodules are populated
ls third_party/stable-diffusion.cpp/  # non-empty
git submodule status                  # every entry begins with a commit SHA, no "-"

# 3. Build with the local JDK 17
./gradlew test --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon

# 4. Install in place on the physical device (never uninstall/clear)
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# 5. Launch and confirm the app reaches a usable screen
adb shell monkey -p <applicationId> 1
adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'
```

The debug `applicationId` is `excp.rikkahub.local.debug` (base
`excp.rikkahub.local` + `.debug` suffix).

## Record to attach to the release gate

| Field | Value |
|-------|-------|
| Device model | e.g. Pixel 10 Pro |
| Android version | e.g. 17 |
| Device serial | adb serial |
| Commit SHA | `git rev-parse HEAD` from the clean clone |
| APK used | `app/build/outputs/apk/debug/app-universal-debug.apk` |
| Unit tests | `./gradlew test --no-daemon` result |
| Lint | `./gradlew lint --no-daemon` result |
| Assemble | `./gradlew assembleDebug --no-daemon` result |
| Foreground after launch | resolved `RouteActivity` component |

## Hard rules

- Install the development APK with `adb install -r` only.
- Never `adb uninstall`, `pm clear`, or data-directory deletion on an install used
  for verification unless it is a disposable profile explicitly set up for a
  destructive test.
- Never run `./gradlew :app:connectedDebugAndroidTest` against a physical phone
  holding real data: AGP uninstalls the target app package after the run.
