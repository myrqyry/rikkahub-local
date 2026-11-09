package me.rerere.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolApprovalStateTest {
    @Test
    fun `approved denied and answered states can resume tool execution`() {
        assertTrue(ToolApprovalState.Approved.canResumeToolExecution())
        assertTrue(ToolApprovalState.Denied("no").canResumeToolExecution())
        assertTrue(ToolApprovalState.Answered("""{"answers":{"q1":"yes"}}""").canResumeToolExecution())
    }

    @Test
    fun `auto and pending states cannot resume tool execution`() {
        assertFalse(ToolApprovalState.Auto.canResumeToolExecution())
        assertFalse(ToolApprovalState.Pending.canResumeToolExecution())
    }

    @Test
    fun `started auto tool with no output is an interrupted attempt`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "auto", toolName = "send_message", input = "{}",
            approvalState = ToolApprovalState.Auto, executionStartedAt = 1234L,
        )
        assertTrue(tool.isInterruptedAttempt)
        assertFalse(tool.copy(executionStartedAt = null).isInterruptedAttempt)
        assertFalse(tool.copy(output = listOf(UIMessagePart.Text("sent"))).isInterruptedAttempt)
    }
}
