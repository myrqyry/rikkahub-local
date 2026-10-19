# RikkaHub project-filtered drawer — 2026-10-18

## Intent and scope

Implement the iterated phone UI mockups in the real Jetpack Compose chat drawer. Keep a **320dp partial-width modal drawer** instead of a full-screen pane; remove the old 56dp navigation rail without removing its destinations.

## Implemented

- Top: raised current-assistant avatar, name, and time-based greeting. A 44dp horizontal swap action anchored on the right opens the original assistant picker. Tapping the avatar still opens assistant settings.
- Main filter: single project dropdown, default **All chats**. Underlying records remain existing assistant-scoped folders. The default now pages all current-assistant conversations (including those assigned to folders); choosing a project pages only that folder's chats.
- In All chats, a tiny project folder icon identifies conversations with a project. In a selected project, no badge is shown. No database/Room migration, schema renaming, or artificial project records.
- Create, select, rename, delete projects through the dropdown and existing folder repository and confirmation dialogs. Long-press chat menu continues to support moving conversations to/from projects, assistants, pinning, title regeneration, and deletion.
- History: remove date and pinned section headers, keep pin/loading indicators, retain paging and scrolling, maintain minimum 44dp touch rows with reduced padding. Untitled sessions display **creation date and local time** in place of “New message,” without mutating stored titles. Named sessions show a compact last-updated time/date on the right.
- Quick actions: new chat by the project picker; the overflow holds Search, History, Models, Images, Assistants, Extensions, Favorites, and Statistics.
- Bottom: user avatar and editable name, with Settings anchored at the right. Existing avatar editing remains available.

## Verified

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`: BUILD SUCCESSFUL, 1,497 app JVM tests from 215 suites with zero failures/errors/skips.
- `git diff --check`: pass.
- In-place `adb install -r` of arm64 debug APK on Pixel 10 Pro: Success. Existing package identity and data preserved.
- Verified a compressed database/datastore/shared-preferences backup with SHA-256 prior to installation. Retained outside Git in private Meristem state.
- On-device visual and accessibility inspection: drawer partial width, real assistant name/avatar/greeting, right-anchored swap, all-chat label, dated untitled sessions, no day headings, bottom user/settings, all with labels. Horizontal swap opens the existing assistant picker.
- On-device project dropdown listed the existing “though” folder. Selecting it showed the expected empty-project state; restoring the default All chats view showed chat history again. No project or conversation data changed.
- Phone foreground returned to ChatGPT after inspection. No push or remote publication.

## Boundaries

- “Projects” are a new presentation/filter for the **existing assistant-scoped folders**, not a global project entity shared by assistants. Custom per-project icons and true project metadata do not exist yet; badges are a generic folder icon. A populated project's badge presentation has not been separately tested on-device.
- New Chat does not yet automatically assign the currently filtered project; existing Move to project action is available.
- No live tablet, light theme, or accessibility font-scaling review performed. The verification proves the observed Pixel dark-theme path, not every responsive surface.
- No unrelated settings, models, provider integrations, database migrations, or installed inference files were changed.


## Correction — failed title generation (2026-10-18)

The original mockup's date-only rows were not the intended everyday presentation. A successful model-generated title always wins. When the stored title is blank, the list now obtains the *first user message* from a single lightweight Room query on the first message node, normalizes whitespace, and uses a short preview. Only chats with no decodable user text fall back to the creation date/time.

This preview is **read-only presentation state**: it is not persisted as `Conversation.title`, so the existing background title generator continues trying and a later generated title automatically replaces it. The selected assistant/project paging and original conversation content remain unchanged.

Verification: targeted `UntitledConversationPreviewTest` (3 new cases), full app JVM suite 1,500/1,500 with zero failures, debug APK build, in-place Pixel installation after SHA-256-verified private data backup, and real on-device UI hierarchy showing previously date-only chats as first-message previews while the existing named chat retained its title. Phone returned to ChatGPT. No push.
