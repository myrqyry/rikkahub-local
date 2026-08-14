package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.CandidateScore
import me.rerere.locallm.litert.ShadowPlanner
import me.rerere.locallm.litert.ActionPlan

/**
 * Roadmap C3 — persist every shadow-candidate score, compiler result, repair
 * penalty, capability coverage, and winner selection as [AgentRunEvent] trace
 * events so a run can explain exactly why a plan was (or was not) selected,
 * without storing hidden chain-of-thought.
 *
 * Events: CANDIDATE_PROPOSED, CANDIDATE_COMPILED, CANDIDATE_RANKED,
 * CANDIDATE_SELECTED. Best-effort: any append failure is swallowed.
 */
class CandidateEvaluationTraceSink(
    private val trace: AgentRunTraceRepository,
) {

    /**
     * Persist the full evaluation for one primary plan. [selection] carries the
     * ranked outcome; [runId] must reference a live (non-terminal) agent run.
     */
    suspend fun record(runId: String, plan: ActionPlan, selection: ShadowPlanner.Selection) {
        val ranked = when (selection) {
            is ShadowPlanner.Selection.Selected -> selection.ranked
            is ShadowPlanner.Selection.AllInvalid -> selection.ranked
        }
        runCatching {
            trace.append(runId, NewAgentRunEvent(
                type = AgentRunEventType.CANDIDATE_PROPOSED,
                summary = "shadow evaluation for plan '${stepLabel(plan)}'",
                operationId = stepLabel(plan),
                payloadJson = buildJsonObject {
                    put("plan_type", planType(plan))
                    put("candidate_count", ranked.size)
                }.toString(),
            ))
            for (score in ranked) {
                trace.append(runId, NewAgentRunEvent(
                    type = AgentRunEventType.CANDIDATE_COMPILED,
                    severity = if (score.compileOutcome == "invalid") TraceSeverity.WARNING else TraceSeverity.INFO,
                    summary = "candidate '${score.candidateId}' compiled as ${score.compileOutcome}",
                    operationId = score.candidateId,
                    payloadJson = candidatePayload(score, selected = false).toString(),
                ))
            }
            trace.append(runId, NewAgentRunEvent(
                type = AgentRunEventType.CANDIDATE_RANKED,
                summary = "ranked ${ranked.size} candidates; best '${ranked.firstOrNull()?.candidateId ?: "none"}'",
                operationId = stepLabel(plan),
                payloadJson = buildJsonObject {
                    put("ranking", ranked.joinToString(",") { it.candidateId })
                }.toString(),
            ))
            when (selection) {
                is ShadowPlanner.Selection.Selected -> trace.append(runId, NewAgentRunEvent(
                    type = AgentRunEventType.CANDIDATE_SELECTED,
                    summary = "selected '${selection.score.candidateId}'",
                    operationId = selection.score.candidateId,
                    payloadJson = candidatePayload(selection.score, selected = true).toString(),
                ))
                is ShadowPlanner.Selection.AllInvalid -> trace.append(runId, NewAgentRunEvent(
                    type = AgentRunEventType.CANDIDATE_SELECTED,
                    severity = TraceSeverity.WARNING,
                    summary = "no valid candidate; execution refused",
                    operationId = stepLabel(plan),
                ))
            }
        }
    }

    private fun candidatePayload(score: CandidateScore, selected: Boolean): JsonObject = buildJsonObject {
        put("candidate_id", score.candidateId)
        put("source", score.source)
        put("compile_outcome", score.compileOutcome)
        put("score", score.score)
        put("capability_coverage", score.capabilityCoverage)
        put("selected", selected)
        put("repairs", score.repairs.joinToString("; "))
        put("reasons", score.reasons.joinToString("; "))
    }

    private fun planType(plan: ActionPlan): String = when (plan) {
        is ActionPlan.ToolCall -> "tool"
        is ActionPlan.WorkflowCall -> "workflow"
        is ActionPlan.ProcedureCall -> "procedure"
    }

    private fun stepLabel(plan: ActionPlan): String = when (plan) {
        is ActionPlan.ToolCall -> plan.toolName
        is ActionPlan.WorkflowCall -> plan.workflowId
        is ActionPlan.ProcedureCall -> plan.procedureId
    }
}
