package me.rerere.locallm.litert

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart

/**
 * The typed intent emitted by a model / router. Never executes directly — deterministic
 * layers compile, simulate and verify; the capability broker authorizes; executors act.
 */
@Serializable
sealed interface ActionPlan {
    val grant: CapabilityGrant

    @Serializable
    data class ToolCall(
        val toolName: String,
        val args: JsonObject,
        override val grant: CapabilityGrant,
    ) : ActionPlan

    @Serializable
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
    @Serializable
    data class ProcedureCall(
        val procedureId: String,
        val inputs: JsonObject,
        override val grant: CapabilityGrant,
    ) : ActionPlan
}

@Serializable
data class CapabilityGrant(
    val requestedCapabilities: List<String>,
    val grantedCapabilities: List<String>,
    val rejectedCapabilities: List<String>,
    val scopes: CapabilityScopes = CapabilityScopes(),
) {
    fun isAllowed(capability: String): Boolean = capability in grantedCapabilities
}

@Serializable
sealed interface ActionPlanResult {
    @Serializable
    data class Success(val output: List<UIMessagePart>) : ActionPlanResult

    @Serializable
    data class Failed(val errorMessage: String) : ActionPlanResult

    @Serializable
    data class CapabilityRejected(val capability: String, val reason: String) : ActionPlanResult
}
