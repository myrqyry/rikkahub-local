# RikkaHub soft-material sidebar — 2026-10-16

## Intent and direct references

Carry over the interaction principles of the OpenCode desktop
sidebar into RikkaHub Local, using the existing Compose Material
palette and neumorphic-ui native primitives, not copied desktop CSS.

Inspected in local OpenCode checkout:
- packages/app/src/pages/layout/sidebar-shell.tsx: stable icon rail
  beside the changing project/session pane
- packages/app/src/pages/layout/sidebar-project.tsx: quiet headings
  and session content hierarchy
- packages/app/src/index.css: light/shadow pairs, selected surface
  tints, brief press motion and clear interactive states.

No OpenCode source modified. Its unrelated local worktree changes
remained untouched.

## Changes

- Chat drawer now 320dp, with a 56dp icon rail and independently
  scrolling session panel. Rail top: New Chat, Search, History.
  Rail bottom: Models, Images, Settings, More.
- Overflow retains Assistants, Extensions, Favorites, Statistics.
  Removed redundant action/footer strips from session panel.
- Rail controls: minimum 44dp hit area, accessibility labels,
  localized strings, soft raised material, pressed-in inset
  feedback and 140ms 0.96-scale tactile motion.
- Raised identity surface for current user avatar, nickname edit,
  and greeting; theme-aware colors from NeumorphicBridge.
- Folder filters: raised idle pills, recessed active pill;
  long-press rename/delete preserved.
- Conversation history: softly recessed selected chat,
  quieter date/pinned headings, 44dp row targets. Preserved
  paging and scroll restoration, long-press actions, pin/loading
  indicators and bottom assistant picker.

## Verification and preservation

- Offline Gradle local-llm testDebugUnitTest: 507 passed.
- Offline Gradle ai testDebugUnitTest: 200 passed.
- Offline Gradle app testDebugUnitTest: 1497 passed.
- Offline app assembleDebug: passed. Total 2204 JVM tests,
  no failures or errors.
- In-place Pixel 10 Pro debug APK installation via adb install -r.
  No uninstall, data reset, model overwrite or database migration.
- Prior to installation verified database/settings backup:
  ~/.local/state/meristem/rikkahub-backups/pre-soft-drawer-20261016-005616.tar.gz
  plus SHA-256 sidecar. Existing local LFM2.5 LiteRT weights intact.
- Actual on-device screenshot inspected for the dark theme:
  navigation rail, session panel, identity surface, folder
  filters, conversations, bottom assistant selector all rendered.
  Screenshots remain uncommitted since they include user state.
- Android accessibility tree contained all seven navigation
  labels. Overflow popup exposed its four original destinations
  and dismissal left drawer open.
- Folder long-press exposed Rename and Delete.
- Conversation long-press exposed Pin, Regenerate title,
  Move to Assistant, Move to Folder and Delete.
  All tests dismissed menus without triggering data changes.
- Closed the drawer to restore original New Chat view. App process
  running and existing loopback inference endpoint not modified.

## Boundaries

This pass addresses the chat sidebar rather than the entire UI.
No composer, model picker, settings, theme engine, OpenCode desktop
worktree or inference changes. Dark-theme Pixel inspected directly;
light mode, larger tablet/permanent drawer and reduced-motion
settings not separately checked on-device.

A good next iteration would use the same raised/inset and short
press motion vocabulary in the composer and model picker, without
raising every scrolling row or compromising contrast.
