package me.rerere.rikkahub.data.ai.tools.safety

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSafetyPreflightTest {
    private val provenance = ExecutionProvenance(source = "user")

    @Test
    fun externalShareRequiresApprovalAndDestination() {
        val plan = ToolExecutionPlan(
            operationId = "op-1",
            toolName = "share",
            effects = setOf(ToolEffect.SHARE_EXTERNALLY),
            dataEgress = listOf(DataEgress("image", "android_chooser", "one_artifact")),
            provenance = provenance,
        )

        val decision = ToolSafetyPreflight().evaluate(plan)

        assertEquals(SafetyDecision.REQUIRE_APPROVAL, decision.decision)
        assertEquals(ApprovalRequirement.USER, decision.requiredApproval)
    }

    @Test
    fun localReadIsAllowedWithoutApproval() {
        val plan = ToolExecutionPlan(
            operationId = "op-2",
            toolName = "read_file",
            effects = setOf(ToolEffect.READ_LOCAL_DATA),
            provenance = provenance,
        )

        assertEquals(SafetyDecision.ALLOW, ToolSafetyPreflight().evaluate(plan).decision)
    }
}
