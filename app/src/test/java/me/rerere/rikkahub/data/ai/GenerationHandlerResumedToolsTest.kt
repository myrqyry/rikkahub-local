package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationHandlerResumedToolsTest {
    private fun tool(
        id: String,
        state: ToolApprovalState,
        output: List<UIMessagePart> = emptyList(),
        started: Long? = null,
    ) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = "example_tool",
        input = "{}",
        approvalState = state,
        output = output,
        executionStartedAt = started,
    )

    @Test
    fun `resume runs approved tool and unstarted auto sibling in original order`() {
        val original = listOf(
            tool("auto-first", ToolApprovalState.Auto),
            tool("approved", ToolApprovalState.Approved),
            tool("auto-last", ToolApprovalState.Auto),
        )
        assertEquals(
            listOf("auto-first", "approved", "auto-last"),
            resumableToolsIncludingUnexecutedAuto(original).map { it.toolCallId },
        )
    }

    @Test
    fun `resume does not rerun started auto or already completed tools`() {
        val tools = listOf(
            tool("started", ToolApprovalState.Auto, started = 123L),
            tool("done", ToolApprovalState.Auto, output = listOf(UIMessagePart.Text("ok"))),
            tool("approved", ToolApprovalState.Approved),
            tool("approved-started", ToolApprovalState.Approved, started = 321L),
        )
        assertEquals(
            listOf("approved"),
            resumableToolsIncludingUnexecutedAuto(tools).map { it.toolCallId },
        )
    }

    @Test
    fun `started auto tool triggers durable receipt persist even before output`() {
        val startedAuto = tool("auto", ToolApprovalState.Auto, started = 1L)
        val startedApproved = tool("approved", ToolApprovalState.Approved, started = 2L)
        assertTrue(shouldPersistToolStartReceipt(listOf(startedAuto)))
        assertTrue(shouldPersistToolStartReceipt(listOf(startedApproved)))
        assertFalse(shouldPersistToolStartReceipt(listOf(tool("new", ToolApprovalState.Auto))))
        assertFalse(shouldPersistToolStartReceipt(listOf(
            tool("done", ToolApprovalState.Auto, output = listOf(UIMessagePart.Text("done")), started = 1L),
        )))
    }

    @Test
    fun `resume preserves pending and handles answered and denied without rerunning them`() {
        val tools = listOf(
            tool("pending", ToolApprovalState.Pending),
            tool("denied", ToolApprovalState.Denied("not allowed")),
            tool("answered", ToolApprovalState.Answered("no")),
            tool("completed-denied", ToolApprovalState.Denied("no"), output = listOf(UIMessagePart.Text("done"))),
        )
        val picked = resumableToolsIncludingUnexecutedAuto(tools)
        assertEquals(listOf("denied", "answered"), picked.map { it.toolCallId })
        assertFalse(picked.any { it.isPending })
        assertTrue(picked.all { !it.isExecuted })
    }
}
