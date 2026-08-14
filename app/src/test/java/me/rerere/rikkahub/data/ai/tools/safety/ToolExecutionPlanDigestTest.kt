package me.rerere.rikkahub.data.ai.tools.safety

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolExecutionPlanDigestTest {

    private val provenance = ExecutionProvenance(source = "user", assistantId = "asst-1")

    private fun base() = ToolExecutionPlan(
        operationId = "op-1",
        toolName = "upload",
        effects = setOf(ToolEffect.READ_LOCAL_DATA, ToolEffect.UPLOAD_DATA),
        dataEgress = listOf(DataEgress("file", "s3://bucket", "project/notes.md")),
        resourceMutations = listOf(ResourceMutation("local://artifacts", "write")),
        privilegeLevel = PrivilegeLevel.NORMAL,
        inputSummary = "upload notes.md to bucket",
        provenance = provenance,
    )

    @Test
    fun changingAnySecurityRelevantFieldInvalidatesDigest() {
        val original = base().digest()

        assertNotEquals(original, base().copy(operationId = "op-2").digest())
        assertNotEquals(original, base().copy(toolName = "sync").digest())
        assertNotEquals(original, base().copy(effects = base().effects + ToolEffect.SEND_MESSAGE).digest())
        assertNotEquals(original, base().copy(effects = setOf(ToolEffect.READ_LOCAL_DATA)).digest())
        assertNotEquals(
            original,
            base().copy(dataEgress = listOf(DataEgress("file", "s3://other", "project/notes.md"))).digest(),
        )
        assertNotEquals(
            original,
            base().copy(resourceMutations = listOf(ResourceMutation("local://artifacts", "delete"))).digest(),
        )
        assertNotEquals(original, base().copy(privilegeLevel = PrivilegeLevel.SENSITIVE).digest())
        assertNotEquals(original, base().copy(inputSummary = "upload different file").digest())
        assertNotEquals(original, base().copy(provenance = ExecutionProvenance(source = "workflow")).digest())
        assertNotEquals(
            original,
            base().copy(provenance = ExecutionProvenance(source = "user", assistantId = "asst-2")).digest(),
        )
    }

    @Test
    fun reorderingSemanticallyUnorderedSetDoesNotChangeDigest() {
        val a = base().copy(effects = setOf(ToolEffect.READ_LOCAL_DATA, ToolEffect.UPLOAD_DATA))
        val b = base().copy(effects = setOf(ToolEffect.UPLOAD_DATA, ToolEffect.READ_LOCAL_DATA))
        assertEquals(a.digest(), b.digest())
    }

    @Test
    fun reorderingMeaningfulListChangesDigest() {
        val egress = listOf(
            DataEgress("file", "s3://a", "x.md"),
            DataEgress("image", "s3://b", "y.png"),
        )
        val a = base().copy(dataEgress = egress)
        val b = base().copy(dataEgress = egress.reversed())
        assertNotEquals(a.digest(), b.digest())
    }

    @Test
    fun nullAndEmptyAreDistinctAndStable() {
        val withNullSummary = base().copy(inputSummary = null)
        val withEmptySummary = base().copy(inputSummary = "")
        val withNullAssistant = base().copy(provenance = ExecutionProvenance(source = "user"))
        val withAssistant = base().copy(provenance = ExecutionProvenance(source = "user", assistantId = "asst-1"))

        assertNotEquals(withNullSummary.digest(), withEmptySummary.digest())
        assertNotEquals(withNullAssistant.digest(), withAssistant.digest())
        // deterministic: same plan -> same digest
        assertEquals(withNullSummary.digest(), withNullSummary.digest())
    }
}
