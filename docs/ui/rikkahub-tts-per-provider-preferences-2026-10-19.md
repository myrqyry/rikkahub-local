# Per-provider TTS reading preferences — 2026-10-19

## Intent

The user wants the Speech screen to show settings appropriate for each
TTS provider, instead of one large global switch block.

Existing voice/model/API controls are already rendered per provider
subtype (e.g. System speech rate/pitch, NekoSpeak speed/pitch, Scylla
delivery/language, and cloud provider-specific models/voice IDs).
The three reading switches are application-side text preprocessing, not
synth capabilities, and can apply to every provider; users can still
customize them independently for each selected provider.

## Changes

- Removed the oversized top-level TTS settings block from Speech.
- Every Add/Edit provider sheet now includes a compact Message reading
  section, immediately above that provider's real engine-specific controls:
  quoted text only, skip bracketed text, and auto-play replies.
- Saved flags live in DisplaySetting.ttsPlaybackProfiles by stable provider
  instance ID, not by engine class. Manual and automatic speech both
  consult the actual selected provider's options.
- Old saved global switches remain as inherited baseline for providers
  with no explicit profile. Existing user behavior does not reset on upgrade.
  Newly added providers receive a saved initial inherited profile.
- Local Add/Edit drafts commit provider parameters plus its playback
  flags together through the serial SettingsStore transform. Cancel
  discards drafts. Provider selection, reordering, and explicit deletion
  also use targeted store updates so they cannot overwrite recent profiles
  using a stale full-settings snapshot. Deletion prunes only that profile.
- Removed unused global switch mutator and test.

## Validation and remaining uncertainties

- Tests cover old-format JSON compatibility, per-provider isolation,
  JSON round trips, selected-provider lookups, strict text filtering
  and auto-play eligibility.
- Full app test suite + debug APK: 1,524 tests, 222 suites, zero failed,
  errored or skipped; BUILD SUCCESSFUL.
- No phone appeared in ADB on yuzu-mint, and the other connected desktop
  did not have an ADB connection. This build was **not installed** and
  has not undergone UI or real TTS audio smoke testing. Existing phone
  settings, model files and conversations were not changed.
- Next device smoke: open Speech, confirm top block removed, inspect each
  provider's engine-specific fields and Message reading section, set
  different options on two providers, swap selections and verify playback
  behavior, then reopen and verify persistence and Cancel/Save.
- Keep this local; unpublished branch history must not be pushed by default.


## Physical Pixel 10 Pro smoke — 2026-10-19

- The Pixel reconnected to yuzu-mint via USB ADB.
- Before installing, captured the app databases, settings datastore
  and shared preferences into a SHA-256-verified private archive. No
  uninstallation or app-data clearing occurred.
- Installed arm64 debug APK in place: success.
- Navigated Settings → Experience → Speech Services. The Android UI
  hierarchy and screenshots confirmed that the large global switch
  block is gone, existing providers are present, and Scylla remains selected.
- Scylla Edit shows Message reading: quoted-only, skip bracketed and
  auto-play, followed by its model-specific controls.
- Cancel smoke: toggled quoted-only in the unsaved Scylla draft, pressed
  Cancel and reopened. All three values remained false; no persistent
  voice or playback settings were deliberately changed.
- The NekoSpeak editor also has provider-specific reading options.
  This test revealed that NekoSpeak's local model file chooser was
  incorrectly labeled API Key. Corrected the heading to Model file,
  rebuilt and reinstalled the APK in place, and confirmed the new
  label and absence of API Key in its Android UI hierarchy.
- Final build and test results: 1,524 app tests across 222 suites,
  zero failures/errors/skips; debug APK built successfully.
- Returned ChatGPT to the foreground and verified resumed activity.

Not yet tested: a real persisted Save to change a provider preference,
audio behavior with selected-provider differences, and background
auto-play. No audio triggered and no user data cleared. No push.
