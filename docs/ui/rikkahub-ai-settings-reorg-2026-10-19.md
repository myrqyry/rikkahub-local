# AI & Models settings simplification — 2026-10-19

## Intent

The settings taxonomy duplicated working model configuration and exposed
unrelated prompt and floating translation features. The floating translation
launcher crashed the app. Keep entry points sparse, predictable, and grounded
in existing functional screens. Preserve user data, assistant configurations,
and unfinished translation source code.

## Changes

- **AI & Models** contains exactly two destinations: Models -> existing
  unified ModelsPage, and Agents & Assistants -> a small hub.
- **Agents & Assistants** has (1) Default assistant -> the existing detail
  editor for the bundled default assistant ID when present (otherwise first
  stored assistant), and (2) Individual assistants -> existing assistant list
  and per-assistant editors. No global agent-default inheritance mechanism
  is fabricated; per-assistant options are the existing stored configuration.
- **Chat Preferences** links to Default prompts -> the existing global
  translation/title/suggestion/OCR/compression prompt editors, and Prompt
  library -> the existing quick message/prompt library. The screen heading
  is now Chat Preferences rather than the vague General.
- **Floating translate bubble**: remove its Settings launcher and manifest
  activity declaration. Its unused source is preserved for later debugging.
  The crash is *not fixed*; app launch through the old in-app action is
  unavailable, and the installed package does not register that activity.
- No database schema migration, assistant rewrite, model assignment changes,
  new inference behavior, or external publication.

## Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` succeeded:
  1,501 app JVM tests, 0 failures/errors/skips. `git diff --check` passed.
- Before installation, verified a checksum-backed private archive of
  rikka_hub database including its WAL, datastore settings and shared prefs.
  `adb install -r` succeeded on Pixel 10 Pro without data clearing.
- Actual on-device UI hierarchy: AI & Models shows only Models and
  Agents & Assistants; latter shows Default assistant and Individual
  assistants. Default opens real assistant (meristem) edit page; individual
  route opens real list of assistants.
- Experience -> Chat Preferences shows links to Default prompts and
  Prompt library. Default prompts navigates to the pre-existing global
  editors for translation, title, suggestions, OCR and context compression.
- Installed package inspection: no TranslateBubbleActivity declaration.
- No direct crash reproduction after disabling the entry point: the
  known-broken feature was removed from access, not repaired.

## Boundaries

The existing assistant editor itself may still have substantial padding and
is not remodeled here. "Default assistant" is a concrete assistant record,
not a new global inheritance layer. Model settings remain handled by the
already-working unified ModelsPage. Private unpublished intermediate commits
on this public repository branch mean **do not push HEAD directly**.
