# Trust and Observability Foundation Design

**Date:** 2026-08-06
**Repository:** `myrqyry/rikkahub-local`
**Status:** Design approved by user; implementation pending

## Goal

Add a durable trust and observability foundation for autonomous runs without turning `AgentRun` into an unbounded detail table. The first milestone covers three connected seams:

1. An append-only, inspectable run event stream.
2. Conversation revision guards for asynchronous results.
3. Effect-aware tool execution plans with deterministic safety preflight.

The first real integration should exercise one existing execution path end to end rather than only adding unused domain types.

## Architecture Boundary

### AgentRun: lifecycle index

`AgentRun` remains the durable, searchable summary and recovery authority. It contains only fields needed for listing, recovery, retention, and fast status queries:

- `id`
- `parentRunId`
- `source`
- `conversationId?`
- `branchId?`
- `status`
- `currentStage?`
- `startedAt`
- `updatedAt`
- `completedAt?`
- `terminalReason?`

Existing recovery and retention behavior must remain intact. New functionality extends this model only where required for summary queries or correctness.

### AgentRunEvent: immutable evidence

Add a Room entity with:

- `id`
- `runId`
- `sequence`
- `type`
- `createdAt`
- `severity`
- `summary?`
- typed query columns for important filters, initially including `toolName?`, `operationId?`, and `effectCategory?`
- bounded `payloadJson?` for event-specific details

Indices:

- unique `(runId, sequence)`
- `(runId, createdAt)`
- `(type)`
- any additional typed filter columns introduced by the first integration

A foreign key from `AgentRunEvent.runId` to `AgentRun.id` uses cascade deletion.

Important query and correctness semantics must not be hidden exclusively in JSON. JSON is for variable details such as MIME types, artifact references, and redacted destinations.

## Event Vocabulary

Use a stable, restrained vocabulary:

- `RUN_CREATED`
- `RUN_STARTED`
- `STAGE_CHANGED`
- `MODEL_RESOLVED`
- `MODEL_CALL_STARTED`
- `MODEL_CALL_COMPLETED`
- `MODEL_CALL_FAILED`
- `TOOL_PROPOSED`
- `TOOL_REVIEWED`
- `TOOL_APPROVED`
- `TOOL_REJECTED`
- `TOOL_STARTED`
- `TOOL_PROGRESS`
- `TOOL_COMPLETED`
- `TOOL_FAILED`
- `TOOL_CANCELLED`
- `PRIVACY_CHECKED`
- `REVISION_CHECKED`
- `RESULT_DISCARDED`
- `ARTIFACT_CREATED`
- `WARNING_RECORDED`
- `RUN_COMPLETED`
- `RUN_FAILED`
- `RUN_CANCELLED`

A terminal event is one of `RUN_COMPLETED`, `RUN_FAILED`, or `RUN_CANCELLED`.

## Trace Repository

Callers use a repository rather than the DAO:

```kotlin
interface AgentRunTraceRepository {
    suspend fun createRun(request: CreateAgentRun): AgentRun
    suspend fun append(runId: String, event: NewAgentRunEvent): AgentRunEvent
    suspend fun recordToolPlan(runId: String, plan: ToolExecutionPlan)
    suspend fun finish(runId: String, outcome: AgentRunOutcome): FinishRunResult
    fun observeRun(runId: String): Flow<AgentRun?>
    fun observeEvents(runId: String): Flow<List<AgentRunEvent>>
}
```

`append` must assign a per-run sequence and update the run summary where appropriate. The event insertion and summary update occur in one Room transaction. A per-run `Mutex` is acceptable for the first implementation; the API must not expose sequencing details to callers.

Repeated terminal calls are idempotent. After a terminal event, ordinary operational events are rejected or ignored through an explicit repository result. Diagnostic/recovery events require a separate explicit path if needed later.

## Conversation Revision Guard

The guard is independent of Room and compares asynchronous work against the conversation state observed at start:

```kotlin
data class ConversationSnapshot(
    val conversationId: String,
    val branchId: String,
    val revision: Long,
)
```

The check result is typed:

- `Match`
- `ConversationMissing(conversationId)`
- `BranchChanged(expectedBranchId, currentBranchId)`
- `RevisionAdvanced(expected, actual)`
- `RevisionRegressed(expected, actual)`

The API includes a future-compatible policy seam:

- `REQUIRE_EXACT_MATCH`
- `ALLOW_DESCENDANT_REVISION`
- `ATTACH_AS_BACKGROUND_RESULT`

The first integration uses exact match for the main assistant response and tool results tied directly to the current turn. Cron and Telegram runs may have no conversation snapshot. Conversation revisions are persisted on `ConversationEntity` and incremented transactionally with repository updates; branch identity currently uses the conversation ID until branching is introduced.

On mismatch, record bounded `REVISION_CHECKED` and `RESULT_DISCARDED` events containing expected/actual branch and revision, policy, result category, and whether an artifact was preserved. Do not store the complete discarded response by default.

## Effect-Aware Tool Plans

Tool plans describe declared operation effects, not approval outcomes:

```kotlin
data class ToolExecutionPlan(
    val operationId: String,
    val toolName: String,
    val effects: Set<ToolEffect>,
    val dataEgress: List<DataEgress>,
    val resourceMutations: List<ResourceMutation>,
    val privilegeLevel: PrivilegeLevel,
    val inputSummary: RedactedValue?,
    val provenance: ExecutionProvenance,
)
```

Initial semantic effect vocabulary:

- `READ_LOCAL_DATA`
- `WRITE_LOCAL_DATA`
- `DELETE_LOCAL_DATA`
- `ACCESS_SENSITIVE_DATA`
- `SEND_NETWORK_REQUEST`
- `UPLOAD_DATA`
- `SHARE_EXTERNALLY`
- `EXECUTE_CODE`
- `INSTALL_COMPONENT`
- `MODIFY_CONFIGURATION`
- `SEND_MESSAGE`

Avoid tool-specific effects; those are capabilities or operations rather than consequences.

Safety review is a separate record:

```kotlin
data class ToolSafetyDecision(
    val decision: SafetyDecision,
    val reasons: List<SafetyReason>,
    val requiredApproval: ApprovalRequirement,
    val decidedBy: DecisionSource,
)
```

Review order:

1. Tool declares a plan.
2. Effects and inputs are normalized and validated.
3. Deterministic policy runs first.
4. Model review is optional and only handles ambiguity.
5. User approval is requested when required.
6. The executor runs the exact approved operation.

Approved operations include an immutable operation ID and plan digest so execution cannot silently diverge from what was approved.

`DataEgress` includes data category, destination, and scope. Android sharing and cloud provider upload are distinct destinations even when both are external data movement.

## Redaction and Limits

Sanitization is enforced centrally, not only documented:

```kotlin
interface TracePayloadSanitizer {
    fun sanitize(payload: TracePayload): SanitizedTracePayload
}
```

Rules:

- Never persist authorization headers, API keys, credentials, or unrestricted environment variables.
- Store artifact references instead of binary/base64 content.
- Hash sensitive paths when the full path is not needed.
- Truncate summaries and tool output.
- Apply a hard serialized event payload limit of 48 KB.
- Large output is stored as an artifact reference.
- Tools may declare sensitive fields.

Trace records are metadata about execution, not a second unrestricted conversation database.

## Retention and Migration

Trace retention aligns with existing `AgentRun` retention:

1. Select expired terminal run IDs.
2. Delete runs in bounded batches.
3. Let the foreign key cascade delete events.
4. Never evict active or recoverable runs solely to meet a count cap.

Add a Room migration from the current schema version with migration tests. Do not synthesize historical event streams for existing runs; old runs may have no events.

## Implementation Order

### Slice 1: trace ledger

- `AgentRunEvent` entity and event vocabulary
- DAO and repository
- Per-run sequencing
- Transactional summary updates
- Terminal invariants and idempotent finish
- Migration, retention, and unit/database tests

### Slice 2: revision guard

- Snapshot provider and typed check results
- Exact-match commit behavior
- Integration into one real assistant response path
- Discard events and branch-isolation tests

### Slice 3: effect-aware plan

- General effect and egress types
- Adapt one existing image/share tool
- Deterministic policy
- Proposed/approved/executed trace events
- Approved-plan identity verification

## End-to-End Acceptance Scenario

The first demonstration should support this sequence:

1. An assistant response begins and creates an `AgentRun`.
2. The conversation snapshot is captured.
3. A share tool is proposed with `SHARE_EXTERNALLY`.
4. Deterministic policy requires approval.
5. Approval and exact plan identity are recorded.
6. The tool starts and emits progress/completion events.
7. The conversation revision changes before the assistant result commits.
8. The result is rejected under `REQUIRE_EXACT_MATCH`.
9. The trace shows the expected/current revision and the discard reason.

## Non-goals for this milestone

- Full trace UI
- Dynamic assistant surfaces
- DAG workflows
- Multi-assistant rooms
- General artifact database redesign
- Cloud/local escalation
- Replacing the existing `AgentRun` recovery implementation
- Storing full prompts, credentials, or unrestricted model/tool output in traces
