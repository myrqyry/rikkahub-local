package me.rerere.rikkahub.data.agentrun

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunEventTest {
    @Test
    fun terminalEventTypesAreRecognized() {
        assertTrue(AgentRunEventType.RUN_COMPLETED.isTerminal)
        assertTrue(AgentRunEventType.RUN_FAILED.isTerminal)
        assertTrue(AgentRunEventType.RUN_CANCELLED.isTerminal)
        assertFalse(AgentRunEventType.TOOL_STARTED.isTerminal)
    }

    @Test
    fun newEventKeepsTypedQueryFieldsSeparateFromPayload() {
        val event = NewAgentRunEvent(
            type = AgentRunEventType.TOOL_PROPOSED,
            severity = TraceSeverity.INFO,
            summary = "Share image",
            toolName = "share",
            operationId = "op-1",
            effectCategory = "SHARE_EXTERNALLY",
            payloadJson = "{\"artifactId\":\"img_1\"}",
        )

        assertTrue(event.toolName == "share")
        assertTrue(event.operationId == "op-1")
        assertTrue(event.effectCategory == "SHARE_EXTERNALLY")
        assertTrue(event.payloadJson!!.contains("img_1"))
    }
}
