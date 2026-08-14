package me.rerere.rikkahub.data.ai.tools.safety

data class ToolSafetyPolicy(
    val requireApprovalForExternalData: Boolean = true,
    val requireApprovalForMutations: Boolean = true,
    val denyCodeExecution: Boolean = false,
)

class ToolSafetyPreflight {
    fun evaluate(plan: ToolExecutionPlan, policy: ToolSafetyPolicy = ToolSafetyPolicy()): ToolSafetyDecision {
        if (plan.operationId.isBlank() || plan.toolName.isBlank() || plan.provenance.source.isBlank()) {
            return ToolSafetyDecision(
                decision = SafetyDecision.DENY,
                reasons = listOf("Operation identity and provenance are required"),
                requiredApproval = ApprovalRequirement.NONE,
                decidedBy = DecisionSource.DETERMINISTIC_POLICY,
            )
        }

        if (ToolEffect.EXECUTE_CODE in plan.effects && policy.denyCodeExecution) {
            return deny("Code execution is disabled by policy")
        }

        if (policy.requireApprovalForMutations && plan.effects.any { it in MUTATING_EFFECTS }) {
            return requireApproval("The operation mutates local or installed resources")
        }

        if (policy.requireApprovalForExternalData && plan.effects.any { it in EXTERNAL_DATA_EFFECTS }) {
            if (plan.dataEgress.isEmpty()) {
                return deny("External data effects require a destination")
            }
            return requireApproval("The operation moves data outside the local workspace")
        }

        return ToolSafetyDecision(
            decision = SafetyDecision.ALLOW,
            reasons = emptyList(),
            requiredApproval = ApprovalRequirement.NONE,
            decidedBy = DecisionSource.DETERMINISTIC_POLICY,
        )
    }

    private fun requireApproval(reason: String) = ToolSafetyDecision(
        decision = SafetyDecision.REQUIRE_APPROVAL,
        reasons = listOf(reason),
        requiredApproval = ApprovalRequirement.USER,
        decidedBy = DecisionSource.DETERMINISTIC_POLICY,
    )

    private fun deny(reason: String) = ToolSafetyDecision(
        decision = SafetyDecision.DENY,
        reasons = listOf(reason),
        requiredApproval = ApprovalRequirement.NONE,
        decidedBy = DecisionSource.DETERMINISTIC_POLICY,
    )

    companion object {
        private val MUTATING_EFFECTS = setOf(
            ToolEffect.WRITE_LOCAL_DATA,
            ToolEffect.DELETE_LOCAL_DATA,
            ToolEffect.EXECUTE_CODE,
            ToolEffect.INSTALL_COMPONENT,
            ToolEffect.MODIFY_CONFIGURATION,
            ToolEffect.SEND_MESSAGE,
        )

        private val EXTERNAL_DATA_EFFECTS = setOf(
            ToolEffect.UPLOAD_DATA,
            ToolEffect.SHARE_EXTERNALLY,
            ToolEffect.SEND_NETWORK_REQUEST,
        )
    }
}
