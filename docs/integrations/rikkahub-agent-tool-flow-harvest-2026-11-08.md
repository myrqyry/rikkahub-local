# RikkaHub Agent upstream harvest — tool execution and multimodal request safety

Date: 2026-11-08. Project: `rikkahub-local`. Baseline: local `aster/mediapipe-parakeet-asr` at `6d5d7c0d9`, with the existing LiteRT benchmark commits preserved.

Upstream source: `ExTV/rikkahub-agent` `master` at `a88f5854b` (2026-09-24). We **adapted** individual upstream fixes rather than merging the upstream branch: the fork's tool approval, replay, hardline guard, model infrastructure, and service boundaries have diverged.

## 1. Resume all safe siblings in a mixed tool-approval batch

Upstream: `1c2ca2dcb` (resume untouched `Auto` siblings), `1913333a6` (exclude interrupted started `Auto` siblings).

Local: `GenerationHandler.kt` previously selected only `canResumeExecution` tools when a user approved/denied a tool. An untouched `Auto` tool in the same batch could be silently orphaned because `Auto.canResumeToolExecution() == false`. `ToolResumeSafety.kt` now selects `Approved`/`Denied`/`Answered` plus untouched `Auto` tools, preserving original order. `Pending`, completed tools, and previously started `Auto`/`Approved` tools are never executed directly by this selector.

**Newly discovered local replay issue:** The previous `isInterruptedAttempt` and `ChatService` immediate-persist predicate only handled `Approved`. Automatic execution also stamps `executionStartedAt` before the side effect; a process death could lose this marker or silently retry the call. Both `Approved` and `Auto` now produce durable start receipts and are converted to a `Denied(interrupted_unknown_outcome)` envelope during replay when the outcome cannot be established. This explicitly does *not* claim the side effect was or was not performed. The existing hardline guard and user approval boundary remain unchanged.

## 2. Stop must not be represented as an ordinary tool error

Upstream: `0658ab587`.

Local: `GenerationHandler.kt` already uses `CancellationException` for generation cancellation, but its inner `runCatching { execute(...) }.onFailure { ... }` caught it and synthesized `tool_failed`. The handler now rethrows cancellation before creating the failure envelope, preventing continued execution of later sibling tools on the stopped turn. Ordinary exceptions retain the established bounded `tool_failed` format.

## 3. Keep parallel image-bearing tool results protocol-valid

Upstream: `f088df5a1`.

Local ChatCompletions: emits all `role:tool` results of a batch contiguously, always with **string** `content`. For image-capable models, the tool images are lifted together in one subsequent `role:user` multimodal content block, preserving tool/image attribution. For text-only models, no image block is sent and the tool string clearly states that the image was omitted.

Local Responses API: image outputs were already included in each `function_call_output.output` as `input_image`, but they were also lifted into separate user items between parallel tool outputs. The redundant lift was removed; each image stays inline with its corresponding tool output.

## Tests / acceptance

- **RED first:** `GenerationHandlerResumedToolsTest` and `GenerationHandlerCancellationTest` initially failed to compile because the new safe selection/cancellation functions did not exist. `ToolApprovalStateTest` then failed behaviorally on a started `Auto` tool, reproducing the interrupted-attempt gap. `ChatCompletionsAPIMessageTest` and `ResponseAPIMessageTest` had three failing JSON-shape tests reproducing non-contiguous tool output, non-string `role:tool` content, missing modality gating, and redundant Responses image lift.
- **GREEN:** focused tests for all three adaptations passed, including missing/started tool exclusions, denial/answer states, image sequencing, type and payload assertions, and text-only behavior.
- **Broader validation:** `./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest :local-llm:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain --max-workers=2` completed successfully (`BUILD SUCCESSFUL`, 354 actionable tasks). `git diff --check` passed.
- No remote provider calls or side-effecting agent flows were executed as part of this acceptance; the tests verify actual message serialization and pure replay/cancellation decisions, not a live end-to-end provider response. Android APKs were built, but no device update was performed as part of this harvest. No user data, installed models or database state was modified.
- Upstream `0ff796986` file source-stream closure and `f85ab6670` auxiliary-job cancellation were already incorporated in the local fork and deliberately *not* copied again.

## Remaining bounds

- Full UI/DB/process-death tool resume acceptance on Android; do not claim that a JVM predicate test proves side effects were not duplicated in the real world.
- Live OpenAI-compatible provider tests covering parallel screenshot/photo tools and provider-specific validation behavior; responses vary across providers.
- Other upstream ideas, especially dedicated OpenRouter image-generation endpoint and throttled background animation, were evaluated but **not implemented** in this slice. They are optional follow-on work, not obligations.
