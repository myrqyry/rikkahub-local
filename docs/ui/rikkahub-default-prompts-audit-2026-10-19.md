# Default prompts and prompt editor — source audit (2026-10-19)

## Scope and boundary

Read the actual five checked-in default templates, prompt editor, interpolation
helper, datastore serialization, and their production call sites.
This is a **static source audit**. No settings, prompt strings, model
assignments, conversations or installed user data were read or changed.
No inference benchmarks or live regeneration tests were run.

Navigation: Settings → Experience → Chat Preferences → Default prompts.
The prompts are **global utility prompts**, not Facet-specific system prompts.

## Findings in priority order

### P1 — compression retains too little explicit continuation context

- `data/ai/prompts/CompressPrompt.kt:3-20`: “preserve key facts” is broad,
  with no required treatment of unresolved tasks, decisions + rationale,
  provenance, uncertainty, corrections, user constraints or failure state.
  Lines 8 and 12 respectively demand conversation language and `{locale}`,
  which can disagree (especially in multilingual conversations).
- `service/ChatService.kt:1304-1392`: splits older messages into groups up to
  256 turns, summarizes each in parallel, and **replaces older message nodes**
  with user-role summary messages while preserving up to 32 recent messages.
  Summaries are not a reversible alternate view of prior messages in the
  active conversation. No semantic loss/regression evaluation is present
  in this path; token targets are prompt requests, not enforced limits.
- `ai/ui/Message.kt:150-158`: `summaryAsText` preserves role prefixes
  but discards non-Text parts (image, audio, document, tools, etc.); the
  prompt cannot preserve information it never receives. Important
  attachment/tool references need deterministic manifests/receipts first.
- Proposed contract: preserve named entities, commitments, decisions *why*,
  constraints, unresolved questions/next steps, what was actually verified,
  distinctions between observation/inference, paths and identifiers as text,
  and pointers to omitted multimodal evidence. Never turn a suggestion or
  model assertion into a user decision. Prefer the language used by the
  conversation/user, with no conflicting locale requirement. If budget is
  insufficient, preserve priority fields and mark omissions, don't invent.

### P1 — editor writes whole settings snapshots for every character

- `ui/pages/setting/SettingModelPromptPage.kt:46-92,153-159`: controlled
  text field on each keystroke calls `vm.updateSettings(settings.copy(...))`.
- `ui/pages/setting/SettingVM.kt:21-25`: launches a separate asynchronous
  `settingsStore.update(settings)` for each edit.
- `data/datastore/PreferencesStore.kt:514-557`: `update(Settings)`
  rewrites **all** prompt keys and many other settings. The same store has
  `update { current -> ... }` with mutex serialization at ~line 617,
  explicitly documented as protection against dropped rapid-fire updates.
- **Risk, not reproduced on device:** rapid typing or another setting edit
  near a stale Compose state snapshot can overwrite a newer value.
- Recommended: controlled *local draft* in the editor, explicit Save/Reset,
  and per-field atomic `settingsStore.update { it.copy(promptField = draft) }`.
  Keep Cancel dismissing unchanged; avoid saving/resetting unrelated fields.
  Optional model-specific preview/test may be added separately.

### P2 — title language and length conflict

- `data/ai/prompts/TitleSummary.kt:3-15`: simultaneously says “user's
  primary language” and “`{locale}` language”; strict 10 **characters**
  is not suitable for descriptive English conversation titles.
- `service/ChatService.kt:1198-1238`: last four messages, each truncated
  to ~500 chars, are sent; the model's trimmed result is saved verbatim,
  without a post-generation nonempty, line-count, or length check.
- Proposed: 3–7 meaningful words (or a generous locale-sensitive cap),
  one line, no surrounding quotes/markdown, use conversation's dominant
  language if obvious, otherwise locale fallback; prefer distinguishing
  objects/topics; do not invent specifics. Validate result separately.
  Existing first-message preview remains a *fallback*, not a generated title.

### P2 — suggested replies are constrained to nonsense brevity

- `data/ai/prompts/Suggestion.kt:3-18`: 3–5 replies of no more than
  **10 characters**, with ambiguous objective “help the assistant improve
  its answers” rather than useful user follow-ups.
- `service/ChatService.kt:1251-1294`: model sees at most eight latest
  messages (up to 500 chars each); parser splits lines, trims, filters blank
  and allows up to **10** (not the prompt's maximum of five). No duplicate,
  length, relevance or role-content validation.
- Proposed: 3–5 distinct, grounded **user** follow-up messages, generally
  a concise sentence rather than a fixed 10-char cap; no generic filler,
  invented events or premature actions; one suggestion per line.
  Normalize/limit duplicates and number after generation.

### P2 — OCR mixes conflicting output purposes

- `data/ai/prompts/OcrPrompt.kt:3-18`: asks for *all text* plus *all*
  nearby nontext elements, positions, spatial relationships, markdown/LaTeX,
  and “who” objects/characters are, but also says *do not interpret*.
  This can obscure a faithful reading-order transcript and invite guesses.
- `data/ai/tools/image/ImageTextExtractor.kt:102-148`: prompt is used as
  a system message for multimodal text generation; when provider is
  `TaskOcrLocal`, dedicated OCR engine is called directly and **does
  not use this prompt**.
- `res/values/strings.xml:796`: editor documents `{images}`, but
  code does not substitute that variable; actual image is sent as an image
  part. Clarify text prompt is relevant only for vision-language OCR.
- Proposed: transcription first (exact text, reading order, layout,
  unreadable/uncertain spans), optional brief non-text observations only
  if useful, no identifying people, no inferred text or object identities.
  Structured markdown/LaTeX only where it preserves real structure.

### P3 — translation template mostly usable but not robust

- `data/ai/prompts/Translation.kt:4-13`: correctly includes
  `{source_text}`, `{target_lang}` and requests just a translation.
  “Elegance” can overemphasize rewriting. Missing explicit treatment of
  code, URLs, proper nouns, units, whitespace, ambiguous passages.
- `data/ai/GenerationHandler.kt:1100-1144`: regular models use this
  prompt; Qwen MT model uses specialized API options instead. Target
  language is passed via `Locale.toString()`, which can be a locale
  tag rather than a readable name.
- Proposed: prioritize fidelity, preserve formatting/identifiers, don't
  hallucinate omitted context, return only translated content; specify
  target locale unambiguously.

### P2 — default lifecycle + misleading editor hints

- `data/datastore/PreferencesStore.kt:239-246`: defaults are read only
  when the corresponding saved key is absent.
- `data/datastore/PreferencesStore.kt:550-557`: every full settings save
  explicitly writes the current prompt strings. Updating bundled Kotlin
  defaults **does not update existing saved copies**.
- `ui/pages/setting/SettingModelPromptPage.kt:60`: title editor reuses
  the suggestion-variable help string (both happen to support
  `{content}` and `{locale}`, so not a runtime blocker).
- `res/values/strings.xml:793`: compression help fails to mention
  supported `{locale}`; OCR help falsely advertises `{images}`.
- Proposed: explicit source-default versioning with one-time migration
  **only for unchanged old defaults**, leaving custom user strings intact;
  alternatively keep old defaults until user explicitly resets. Show
  “Bundled default” vs “Customized” and a diff/preview before updating.

## Verification and follow-up tests

Source-inspected only, not runtime-proven. Suggested test plan:

1. Default prompt expansion by language including English, Japanese and
   mixed-language chats; check placeholders and output formatting.
2. Generated title and suggestions constrained/validated in parser, not
   solely by requesting a format in a natural-language prompt.
3. Prompt editor rapid typing, interleaved settings changes, Cancel/Save,
   Reset, and preservation of custom values after upgrades.
4. Context compression “golden histories” with conflicting claims,
   corrections, files/tool receipts, unresolved implementation steps,
   mixed-language turns, and later reconstruction correctness.
5. OCR image fixtures testing exact text vs visual description and
   dedicated local OCR bypass; check the editor hints against runtime vars.
6. Translation fixtures including literal braces, code, names, mixed scripts.

No source defaults or installed user preferences were changed by this review.
Review-only checkpoint; changes need focused implementation and tests.
No remote push (public repo with local unpublished history).
