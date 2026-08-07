# FunctionGemma Mobile Actions integration tasks

Target model page: https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions

This checklist is intentionally implementation-first. Do not add a RikkaHub-managed model catalog download flow for FunctionGemma.

## Install/import contract — locked

RikkaHub may link the user to the Hugging Face model page for discovery. The user chooses the exact artifact on Hugging Face, accepts any required terms there, copies the artifact URL, returns to RikkaHub, and pastes that URL into the existing generic local-model URL importer.

Do **not**:

- choose an artifact for the user,
- auto-download a FunctionGemma file from a catalog entry,
- add Hugging Face license/auth handling specifically for FunctionGemma,
- bypass the existing `ModelEntry.Source.CustomUrl` / `ModelInstall` path.

The existing importer already normalizes Hugging Face `/blob/<branch>/...` URLs to `/resolve/<branch>/...`, requires HTTPS, recognizes `.litertlm`, stores files privately, and performs basic download sanity checks.

---

## Prerequisite: close the native LiteRT approval bypass

Merge PR #10 first:

`fix(local-ai): fail closed on LiteRT approval-required tools`

Current LiteRT-LM automatic tool calling invokes `LiteRtToolBridge.runTool(...)` inside the SDK. Before PR #10, that bridge called `Tool.execute(...)` directly, bypassing RikkaHub's normal `Pending -> Approved/Denied -> execute` loop.

PR #10 is a temporary fail-closed floor: approval-required tools return `approval_required` instead of executing silently. The tasks below replace that temporary refusal with the proper Rikka approval flow.

---

## Task 1 — surface native LiteRT tool calls instead of auto-executing them

### Files

- `local-llm/src/main/java/me/rerere/locallm/litert/LiteRtRuntime.kt`
- `local-llm/src/main/java/me/rerere/locallm/litert/LiteRtProvider.kt`
- corresponding JVM tests under `local-llm/src/test/java/me/rerere/locallm/litert/`

### 1A. Disable SDK automatic tool execution

In `LiteRtRuntime.createConversationWithFlags(...)`, configure the LiteRT-LM conversation with manual tool calling:

```kotlin
ConversationConfig(
    samplerConfig = ...,
    systemInstruction = spec.systemInstruction,
    tools = spec.tools,
    automaticToolCalling = false,
)
```

Do not merely flip this flag and stop. Once automatic execution is disabled, `Message.toolCalls` must be surfaced to the provider or tool requests will disappear.

### 1B. Replace the text-only runtime stream contract

`LiteRtRuntime.streamTurns(...)` currently returns `Flow<String>` containing cumulative response text. Change the internal contract so an event can carry both cumulative text and native tool calls.

Prefer a local SDK-independent shape such as:

```kotlin
data class NativeToolCall(
    val name: String,
    val argumentsJson: String,
)

data class LiteRtStreamEvent(
    val cumulativeText: String,
    val toolCalls: List<NativeToolCall> = emptyList(),
)
```

Do not expose LiteRT-LM SDK `Message` / `ToolCall` classes beyond the runtime boundary unless unavoidable.

Inside `MessageCallback.onMessage(message)`, retain the existing cumulative-text behavior and additionally convert `message.toolCalls` into `NativeToolCall` values.

Before implementing this conversion, inspect the exact LiteRT-LM 0.11.x `ToolCall` API used by this repository. Do not guess the property names or argument representation.

### 1C. Convert native calls into Rikka tool parts

In `LiteRtProvider.streamText(...)`, convert each native call into the same `UIMessagePart.Tool` representation cloud providers use.

Requirements:

- preserve the native function name,
- preserve arguments as a JSON object string,
- give every call a stable unique `toolCallId`,
- initial approval state must be `ToolApprovalState.Auto`,
- emit `finishReason = "tool_calls"` when native calls are present,
- do **not** call `Tool.execute(...)` in `local-llm`.

This is the critical integration point: once a FunctionGemma/native LiteRT call becomes a normal `UIMessagePart.Tool`, the existing `GenerationHandler` path already provides:

- `HardlineCommandGuard`,
- `Tool.needsApproval(...)`,
- persisted auto-approval checks,
- `Pending` UI state,
- Approved/Denied resume handling,
- loop guarding,
- the regular execution path.

Do not reimplement those policies inside LiteRT.

### 1D. Tool result continuation

After an approved tool executes, the next LiteRT turn must receive the tool result in a form FunctionGemma/LiteRT-LM understands.

Current `LiteRtProvider.renderTurnRawText(...)` reconstructs tool history using generic textual `<tool_call>` / `<tool_result>` blocks. Verify whether FunctionGemma 270M behaves correctly with that representation after a native structured call.

Preferred order:

1. If LiteRT-LM 0.11.x exposes a structured tool-response content API usable with manual tool calling, use that.
2. Otherwise implement a FunctionGemma-specific cold continuation matching its chat/function template.
3. If neither is robust, use FunctionGemma strictly as a one-shot action router: it proposes Rikka tool calls, Rikka executes them, and the regular assistant produces any final conversational response.

Do not let an uncertain tool-response format block the one-shot router mode; that mode is already useful.

---

## Task 2 — add the exact FunctionGemma Mobile Actions compatibility toolset

### New file

Suggested:

`local-llm/src/main/java/me/rerere/locallm/litert/FunctionGemmaMobileActionsToolBridge.kt`

FunctionGemma was fine-tuned against seven specific function names and schemas. Do not make it learn RikkaHub's generic `runTool(name, argsJson)` wrapper if the goal is to preserve the fine-tune's routing accuracy.

Expose these exact LiteRT `@Tool` methods:

```text
turn_on_flashlight()
turn_off_flashlight()
create_contact(first_name, last_name, phone_number?, email?)
send_email(to, subject, body?)
show_map(query)
open_wifi_settings()
create_calendar_event(title, datetime)
```

`datetime` is local ISO date-time in `YYYY-MM-DDTHH:MM:SS` form.

### Map them onto existing Rikka tools

Do not implement Android behavior in `local-llm`. Delegate to the request's existing Rikka tool registry.

| FunctionGemma function | Existing Rikka tool | Argument adaptation |
| --- | --- | --- |
| `turn_on_flashlight` | `set_torch` | `{ "on": true }` |
| `turn_off_flashlight` | `set_torch` | `{ "on": false }` |
| `create_contact` | `create_contact` | same useful fields |
| `send_email` | `send_email_intent` | same `to`, `subject`, optional `body` |
| `show_map` | `show_location_on_map` | `{ "query": ... }` |
| `open_wifi_settings` | `open_wifi_settings` | `{}` |
| `create_calendar_event` | `create_calendar_event` | convert local ISO `datetime` to `start_time_unix_ms` |

For calendar conversion, parse `LocalDateTime` and resolve it in `ZoneId.systemDefault()` unless RikkaHub already has a centralized user-time-zone abstraction that should be used instead.

If the underlying Rikka tool is not enabled for the assistant/request, return a structured `tool_not_registered` error. Never silently enable a tool option just because FunctionGemma requested it.

### Existing Android implementations already cover all seven behaviors

- `TorchTool.kt` provides `set_torch`.
- `SystemIntentTools.kt` provides calendar event, contact, email composer, Wi-Fi settings, and map intents.
- The intent-based actions deliberately leave final save/send behavior to the destination Android app and currently require Rikka approval.

Preserve those semantics. FunctionGemma is the router, not a new privileged execution channel.

---

## Task 3 — select the FunctionGemma ToolSet only for a FunctionGemma action model

### Files

- `LiteRtProvider.kt`
- optionally a new small model-profile helper in the LiteRT package

The normal LiteRT provider should keep its generic `LiteRtToolBridge` for ordinary local models. FunctionGemma should receive the seven exact Mobile Actions declarations instead.

Do not expose both toolsets by default; that wastes the 1024-token action model's context and gives it competing schemas.

### Model identification

Do **not** tie this to a RikkaHub catalog download entry.

Preferred approaches, in order:

1. Add an explicit per-installed-model profile/capability such as `Mobile Actions / FunctionGemma` that the user can select after URL import.
2. If that UI is too invasive for the first slice, recognize the known imported filenames as a compatibility fallback.

Known current repository artifacts include:

- `mobile_actions_q8_ekv1024.litertlm`
- `functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm`

Filename detection must remain a runtime/profile decision only. It must not initiate a download.

---

## Task 4 — give the action router the tiny prompt it was trained for

FunctionGemma Mobile Actions training includes current date/time and day-of-week context so phrases such as "tomorrow at 4" can resolve correctly.

For the FunctionGemma profile, avoid the normal bulky local-agent system prompt. Build a compact instruction containing at least:

```text
Current date and time given in YYYY-MM-DDTHH:MM:SS format: <local datetime>
Day of week is <weekday>
You are a model that can do function calling with the following functions.
```

The LiteRT SDK supplies the function declarations from the `ToolSet`; do not duplicate full JSON schemas in prompt text.

Keep ordinary LiteRT chat models on the existing system-prompt behavior.

---

## Task 5 — FunctionGemma runtime defaults

Do not create a model download catalog entry.

For a recognized/selected FunctionGemma Mobile Actions profile, use conservative action-router defaults rather than the generic local-chat fallback.

For `mobile_actions_q8_ekv1024.litertlm`:

- context/KV target: 1024,
- no image,
- no audio,
- no thinking,
- small output allowance sufficient for one or several function calls,
- constrained/native function calling enabled.

Keep Google Tensor G5/NPU-specific execution as a separate optimization. Do not assume the G5-compiled artifact is interchangeable with the generic CPU/GPU artifact, and do not make NPU support a prerequisite for the first FunctionGemma integration.

---

## Task 6 — tests

### Pure mapping tests

Cover all seven mappings:

- flashlight on -> `set_torch(on=true)`
- flashlight off -> `set_torch(on=false)`
- contact optional fields preserved/omitted correctly
- email -> `send_email_intent`
- map -> `show_location_on_map`
- Wi-Fi -> `open_wifi_settings`
- calendar ISO local datetime -> expected epoch milliseconds using a fixed test timezone

Also test:

- malformed calendar datetime fails closed,
- missing/disabled underlying Rikka tool returns `tool_not_registered`,
- malformed native argument JSON does not execute anything.

### Approval-loop regression tests

The important end-to-end invariant is:

```text
FunctionGemma native call
  -> UIMessagePart.Tool(Auto)
  -> existing needsApproval/hardline checks
  -> Pending when required
  -> no execution before approval
  -> Approved executes exactly once
  -> Denied executes zero times
```

Include a multi-call case so two valid FunctionGemma calls are not collapsed into one.

### Provider/runtime tests

- manual LiteRT tool call is emitted as `finishReason=tool_calls`,
- text preceding a tool call is preserved,
- no SDK-native automatic execution occurs,
- ordinary LiteRT chat without tools remains unchanged,
- ordinary LiteRT models still receive the generic bridge rather than the FunctionGemma toolset.

---

## Task 7 — device smoke test

Use a FunctionGemma `.litertlm` artifact installed **only by pasting its user-selected URL into the existing importer**.

Check:

1. "Turn on the flashlight" proposes/executes `set_torch(true)` through normal approval policy.
2. "Turn off the flashlight" maps correctly.
3. "Open Wi-Fi settings" opens the system page after approval.
4. "Show the Eiffel Tower on the map" opens the map intent.
5. "Add John Doe, 555-..." opens a pre-filled contact screen; RikkaHub does not silently save it.
6. "Email ..." opens a pre-filled composer; RikkaHub does not silently send it.
7. "Schedule lunch tomorrow at noon" uses the current local date context and opens a correctly dated calendar draft.
8. Disable the corresponding Rikka tool option and confirm FunctionGemma gets a safe unavailable/error result rather than gaining the capability anyway.
9. Deny an approval and confirm the underlying Android action never fires.
10. Confirm a normal LiteRT chat model still behaves normally after all FunctionGemma-specific plumbing is installed.

---

## Definition of done

FunctionGemma is complete when it is a fast local action **router**, not a bypass around the rest of RikkaHub:

```text
user text / voice
  -> imported FunctionGemma local model
  -> exact Mobile Actions native function call
  -> Rikka tool-call representation
  -> existing safety + approval policy
  -> existing Android tool implementation
  -> user-visible result / destination app
```

No direct artifact download UI is part of this feature.
