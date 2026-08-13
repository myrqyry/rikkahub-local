package me.rerere.locallm.litert

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPlanExecutorTest {

    @After
    fun teardown() {
        LiteRtToolBridgeRegistry.clear()
    }

    @Test
    fun `ToolCall routes through direct executor`() = runBlocking {
        val executed = java.util.concurrent.atomic.AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("ok"))
                    },
                )
            )
        )
        val plan = ActionPlan.ToolCall(
            toolName = "safe_read",
            args = buildJsonObject { },
            grant = CapabilityGrant(
                requestedCapabilities = listOf("safe_read"),
                grantedCapabilities = listOf("safe_read"),
                rejectedCapabilities = emptyList(),
            ),
        )

        val result = ActionPlanExecutor().execute(plan)

        assertTrue(executed.get())
        assertEquals(listOf(UIMessagePart.Text("ok")), (result as ActionPlanResult.Success).output)
    }

    @Test
    fun `ToolCall with ungranted capability is rejected before execution`() = runBlocking {
        val executed = java.util.concurrent.atomic.AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "secret_read",
                    description = "test",
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("secret"))
                    },
                )
            )
        )
        val plan = ActionPlan.ToolCall(
            toolName = "secret_read",
            args = buildJsonObject { },
            grant = CapabilityGrant(
                requestedCapabilities = listOf("secret_read"),
                grantedCapabilities = emptyList(),
                rejectedCapabilities = listOf("secret_read"),
            ),
        )

        val result = ActionPlanExecutor().execute(plan)

        assertTrue(!executed.get())
        assertTrue(result is ActionPlanResult.CapabilityRejected)
    }

    @Test
    fun `WorkflowCall without zero executor fails loudly`() = runBlocking {
        val plan = ActionPlan.WorkflowCall(
            workflowId = "w1",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )

        val result = ActionPlanExecutor().execute(plan)

        assertTrue(result is ActionPlanResult.Failed)
        assertTrue((result as ActionPlanResult.Failed).errorMessage.contains("workflow_execution_not_implemented"))
    }

    @Test
    fun `WorkflowCall with zero executor delegates to it`() = runBlocking {
        var delegated: String? = null
        val executor = ActionPlanExecutor(
            zeroWorkflowExecutor = ZeroWorkflowExecutor { plan ->
                delegated = plan.workflowId
                ActionPlanResult.Success(listOf(UIMessagePart.Text("wf done")))
            },
        )
        val plan = ActionPlan.WorkflowCall(
            workflowId = "w2",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )

        val result = executor.execute(plan)

        assertEquals("w2", delegated)
        assertEquals(listOf(UIMessagePart.Text("wf done")), (result as ActionPlanResult.Success).output)
    }

    @Test
    fun `successful tool call records a succeeded receipt`() = runBlocking {
        val receipts = mutableListOf<WorkflowReceipt>()
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    execute = { listOf(UIMessagePart.Text("ok")) },
                )
            )
        )
        val executor = ActionPlanExecutor(
            receiptSink = WorkflowReceiptSink { receipts += it },
        )
        val plan = ActionPlan.ToolCall(
            toolName = "safe_read",
            args = buildJsonObject { },
            grant = CapabilityGrant(
                requestedCapabilities = listOf("safe_read"),
                grantedCapabilities = listOf("safe_read"),
                rejectedCapabilities = emptyList(),
            ),
        )

        executor.execute(plan)

        assertEquals(1, receipts.size)
        val receipt = receipts.single()
        assertEquals("tool", receipt.kind)
        assertEquals("safe_read", receipt.domainId)
        assertEquals("valid", receipt.compileOutcome)
        assertEquals("succeeded", receipt.status)
        assertNull(receipt.errorMessage)
        assertTrue(receipt.diagnostics.isEmpty())
        assertTrue(receipt.repairs.isEmpty())
    }

    @Test
    fun `ungranted capability records a capability_rejected receipt`() = runBlocking {
        val receipts = mutableListOf<WorkflowReceipt>()
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "secret_read",
                    description = "test",
                    execute = { listOf(UIMessagePart.Text("secret")) },
                )
            )
        )
        val executor = ActionPlanExecutor(
            receiptSink = WorkflowReceiptSink { receipts += it },
        )
        val plan = ActionPlan.ToolCall(
            toolName = "secret_read",
            args = buildJsonObject { },
            grant = CapabilityGrant(
                requestedCapabilities = listOf("secret_read"),
                grantedCapabilities = emptyList(),
                rejectedCapabilities = listOf("secret_read"),
            ),
        )

        executor.execute(plan)

        assertEquals(1, receipts.size)
        val receipt = receipts.single()
        assertEquals("capability_rejected", receipt.status)
        assertTrue(receipt.errorMessage.orEmpty().isNotEmpty())
    }

    @Test
    fun `invalid plan records an invalid failed receipt without executing`() = runBlocking {
        val receipts = mutableListOf<WorkflowReceipt>()
        val executed = java.util.concurrent.atomic.AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("ok"))
                    },
                )
            )
        )
        val executor = ActionPlanExecutor(
            receiptSink = WorkflowReceiptSink { receipts += it },
        )
        val plan = ActionPlan.ToolCall(
            toolName = "unknown_tool_xyz",
            args = buildJsonObject { },
            grant = CapabilityGrant(
                requestedCapabilities = listOf("unknown_tool_xyz"),
                grantedCapabilities = listOf("unknown_tool_xyz"),
                rejectedCapabilities = emptyList(),
            ),
        )

        val result = executor.execute(plan)

        assertTrue(result is ActionPlanResult.Failed)
        assertTrue(!executed.get())
        assertEquals(1, receipts.size)
        val receipt = receipts.single()
        assertEquals("invalid", receipt.compileOutcome)
        assertEquals("failed", receipt.status)
        assertTrue(receipt.diagnostics.isNotEmpty())
    }
}
