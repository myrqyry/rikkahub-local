package me.rerere.locallm.litert

import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart

sealed interface ActionPlan {
    val grant: CapabilityGrant

    data class ToolCall(
        val toolName: String,
        val args: JsonObject,
        override val grant: CapabilityGrant,
    ) : ActionPlan

    data class WorkflowCall(
        val workflowId: String,
        val inputs: JsonObject,
        override val grant: CapabilityGrant,
    ) : ActionPlan

    /**
     * Phase Zero (canonical roadmap B4) — invoke a stored [me.rerere.locallm.litert.zero.ZeroProcedure]
     * by id. Kept distinct from [WorkflowCall]: Rikka workflows stay on [WorkflowEngine] via
     * [ZeroWorkflowExecutor], while procedure calls route through the deterministic
     * ZeroProcedureEngine via [ZeroProcedureExecutor].
     */
    data class ProcedureCall(
        val procedureId: String,
        val inputs: JsonObject,
        override val grant: CapabilityGrant,
    ) : ActionPlan
}

data class CapabilityGrant(
    val requestedCapabilities: List<String>,
    val grantedCapabilities: List<String>,
    val rejectedCapabilities: List<String>,
    val scopes: CapabilityScopes = CapabilityScopes(),
) {
    fun isAllowed(capability: String): Boolean = capability in grantedCapabilities
}

sealed interface ActionPlanResult {
    data class Success(val output: List<UIMessagePart>) : ActionPlanResult
    data class Failed(val errorMessage: String) : ActionPlanResult
    data class CapabilityRejected(val capability: String, val reason: String) : ActionPlanResult
}
