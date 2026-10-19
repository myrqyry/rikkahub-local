# Chat reasoning/answer overlap and MingCute audit (2026-10-19)

## User-visible failure

A Pixel 10 Pro screenshot of the stored Mario Bros haiku conversation showed
the **Thought for 5.1 seconds** reasoning pill painting over the Japanese
answer text. The user explicitly clarified that the overlap, not only icons,
was the reason for sharing the screenshot.

## Root cause and correction

`ChatMessage.kt` hosted `MessagePartsBlock()` in a Compose `Box` together
with optional translation. `MessagePartsBlock` emits many sibling composables
for a message's reasoning/tool block(s) and text part(s). Compose `Box`
stacks its children at the same origin, rather than measuring them one after
the next. This made reasoning and the answer overlap.

`ProvideTextStyle` now uses a vertical `Column` to contain all message
parts followed by the optional translation; the outer `Box` still owns its
context-menu popup. Each part therefore occupies measured vertical space,
including collapsed/expanded reasoning and the ensuing answer.

## MingCute follow-up

- The screenshot's chat toolbar, action, reasoning, and composer control
  icon sites already use the in-repo `MingcuteIcons` facade.
- Android app source has zero imports of Compose Material icon-pack classes.
- Two remaining generic reasoning pictograms were still using the old
  `R.drawable.deepthink` in the model ability tag and exported reasoning
  step. Both now use `MingcuteIcons.Idea01` consistently with the live
  reasoning card.
- Intentional provider/app branding, file-type artwork (PDF/DOCX),
  avatars and other semantic images are not generic toolbar icons and
  are intentionally not replaced with MingCute symbols.
- This is an audited *targeted* migration; it does not establish that every
  custom image or non-Compose surface has been globally rebranded.

## Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: successful,
  1,501 app unit tests, no failures/errors/skips.
- Before installing, verified a SHA-256-backed private backup of the user's
  app databases, datastore and shared preferences. Installed debug APK with
  `adb install -r`, preserving existing application identity/data.
- Opened the **exact stored conversation** on the Pixel and inspected both
  a real screenshot and Android UI hierarchy: Thought pill ends at y=911;
  the Japanese reply starts at y=955 and English follows at y=1182.
  The original screenshot showed those same elements overlapping.
- The expanded-thought layout was not separately verified on-device in this
  pass because the phone returned to ChatGPT before that interaction; it is
  covered by the same Column placement, not independently proven.
- No model/provider files, downloads, inference runtimes, databases, or
  settings were changed. Do not sweep unrelated Bonsai work into the commit.
- Save changes locally. The public repo branch has unpublished history;
  **do not push**.
