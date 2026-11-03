# Speech screen — TTS option enforcement (2026-10-19)

## User request and screenshot

The Speech page has three switches: TTS Read Quoted Content Only,
TTS Skip Bracketed Content, and Auto-play TTS After Generation.
The task was to ensure actual playback honors them, not just the UI.

## Actual problems found

- Manual playback and auto-play each used a fallback to the original
  message when quoted extraction returned null or bracket filtering
  removed all text. This violated both restrictions.
- Auto-play listened to the global generation-complete event flow without
  checking that the emitted conversation was the current one.
  Duplicate events could replay the same old reply.
- Quoted extraction grouped spans by quotation-mark style rather than
  message order, and apostrophes could be treated as delimiters.
- TTS switch changes used asynchronous full-settings snapshots, risking
  lost unrelated updates after rapid or interleaved changes.

## Changes

- Added one pure prepareTtsText() helper used by manual speech and
  automatic speech. Restricted-out content means no speech, never
  a fallback to forbidden original text.
- When manual playback has no eligible text, a toast explains why.
- Quoted spans now retain text order across quote styles, avoid
  contraction confusion and duplicate nested quotes. Bracket skipping
  supports paired nested (), （）, [], ［］ and curly braces.
- Added eligibleTtsAutoPlay(), checking the completion conversation ID,
  latest VM conversation ID, enabled setting, last message role, previous
  played message ID and shared text policy. Uses live VM conversation state
  at completion instead of a potentially stale UI snapshot.
- TtsDisplayOption updates use SettingsStore's atomic transform so each
  switch preserves all other preferences. UI switch state remains immediate.
- Provider sample preview intentionally bypasses assistant-reply filtering
  so users can still test a voice independently of those switches.

## Validation and limits

- Gradle app JVM tests and debug APK build: BUILD SUCCESSFUL.
  1,521 tests across 222 suites; zero failures, errors and skips.
- New tests cover no quotes, no eligible bracket text, combined filters,
  mixed quotation orders/contractions, paired nested bracket removal,
  wrong-chat/duplicate auto-play, user-only completions, and preserving
  unrelated settings.
- Pixel unavailable in adb devices on yuzu-mint. The other connected
  desktop (doopa) also has no ADB executable. No APK installed or real
  TTS playback exercised this turn. The user's existing app, models,
  saved Speech settings and conversations remain untouched.
- Device smoke remains: test manual speech with each toggle and both
  toggles together, finish chat generation while viewing a different
  conversation, confirm single auto-play and persistence across app restart.
- Separate provider voice settings are already available and not changed.
  No push; current branch contains unpublished local history.
