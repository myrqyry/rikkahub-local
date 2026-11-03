# Chat chrome and composer layout — 2026-10-28

## User request and screenshot context

The previous Android screenshot had a two-line top header (conversation title
and assistant/model/provider), two right-side action icons, a two-row composer
with the model represented only by an icon, and a large bottom attachment
sheet with Extensions and Compress History.

The user asked to:
- keep only the chat title in the top header, replacing the top-right
  action icons with a **three-dot** overflow menu;
- move the existing *in-current-chat* search/preview action to that menu
  (not global Search Chats), and **Compress History** alongside it;
- keep the **plus** at the start of the composer text input and
  **send/stop** at the end, preserving long-press send-without-answer;
- keep a small **provider-logo + selected model name** row beneath the
  input, alongside web search, model-dependent thinking and voice;
- remove **Extensions** and the moved Compress History action from the
  attachments sheet, but do not delete installed extensions/settings.

## Implemented wiring

- `ChatPage.kt`: TopBar renders the conversation title alone. Overflow
  actions are `SEARCH_IN_CONVERSATION` and `COMPRESS_HISTORY`.
  Search triggers the **existing** per-conversation `previewMode` in
  `ChatListPreview`, which has a text search, message snippets and
  jump-to-message behavior; no navigation to global `Screen.MessageSearch`.
  The menu changes to "Close chat search" while that mode is active.
  Compression opens the **existing** `CompressContextDialog` directly,
  calling `ChatVM.handleCompressContext` and retaining its async Job
  lifecycle and warning. Compression is disabled when the chat is empty.
  The drawer still offers creating a new chat.
- `ChatInput.kt`: inside the same rounded composer Surface, the first
  row is attach (+), text input, and send/stop (48dp touch targets).
  Long press keeps the existing no-answer generation behavior. The second
  row has a clickable full model selector, web search, capability-gated
  reasoning control and microphone/ASR when available. Search and thinking
  have not been removed from normal composer tools.
- `ModelList.kt`: the selector has an optional `logoName` argument,
  passed the selected provider display name by the composer. It shows an
  existing `AutoAIIcon` plus the model's **actual** `displayName` while
  preserving the full ModelList bottom sheet selection and existing
  onSelect callback. The visible label is bounded to 220dp and the
  tools row horizontally scrolls on narrow screens. Existing call sites
  remain unchanged when `logoName` is absent.
- `FilesPicker.kt`: removed only the Extensions row and Compress
  History row/dialog from this picker. Camera, photo, video/audio where
  already supported, generic upload, workspace and MCP controls remain.
  Saved quick messages/prompt injections/skills are not deleted; they
  remain available through their dedicated settings surfaces.
- Added localized string resource keys for the overflow labels in the
  base strings file; other locales use the Android fallback.
- `ChatChromeActionsTest` was written RED-first to guard the two
  conversation-scoped menu actions and title-only fallback.

## Verification boundary

The modified Android app compiles into a debug APK and the focused JVM test
is green. The complete AI/app JVM suites and `:app:assembleDebug` are
required and recorded in the active work checkpoint.

**No updated APK was installed on the user's phone as part of this UI
change.** The screenshots preceding the implementation were design
references, not evidence that the new UI has rendered on-device.
When the user chooses to test, verify:
1. Header only shows title, overflow displays Search in chat /
   Compress History, no New Chat action in the header.
2. Search opens the current chat's existing searchable preview and
   returns to messages; compress remains a deliberate action/dialog.
3. Primary row plus/text/send remains usable with keyboard open and
   during loading. Long-press send still works.
4. Secondary model row shows the provider icon and current model name,
   changes after selection, handles very long names horizontally.
5. Bottom sheet shows attachments but no Extensions or Compress History
   rows. Saved extension configurations remain unchanged.
