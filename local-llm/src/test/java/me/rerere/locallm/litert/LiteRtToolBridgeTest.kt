package me.rerere.locallm.litert

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtToolBridgeTest {

    @After
    fun teardown() {
        LiteRtToolBridgeRegistry.clear()
    }

    @Test
    fun `approval-required tool is refused without executing`() {
        val executed = AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "dangerous_action",
                    description = "test",
                    needsApproval = { true },
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("executed"))
                    },
                )
            )
        )

        val result = LiteRtToolBridge().runTool("dangerous_action", "{}")

        assertFalse(executed.get())
        assertTrue(result.contains("\"error\":\"approval_required\""))
    }

    @Test
    fun `auto-approved tool still executes`() {
        val executed = AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    needsApproval = { false },
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("ok"))
                    },
                )
            )
        )

        val result = LiteRtToolBridge().runTool("safe_read", "{}")

        assertTrue(executed.get())
        assertTrue(result == "ok")
    }

    @Test
    fun `grant source restricting the tool rejects it without executing`() {
        val executed = AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    needsApproval = { false },
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("ok"))
                    },
                )
            )
        )
        val bridge = LiteRtToolBridge(
            capabilityGrantSource = CapabilityGrantSource { _ -> emptySet() },
        )

        val result = bridge.runTool("safe_read", "{}")

        assertFalse(executed.get())
        assertTrue(result.contains("\"error\":\"capability_rejected\""))
    }

    @Test
    fun `grant source allowing the tool still executes it`() {
        val executed = AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "safe_read",
                    description = "test",
                    needsApproval = { false },
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("ok"))
                    },
                )
            )
        )
        val bridge = LiteRtToolBridge(
            capabilityGrantSource = CapabilityGrantSource { registered -> registered },
        )

        val result = bridge.runTool("safe_read", "{}")

        assertTrue(executed.get())
        assertTrue(result == "ok")
    }

    @Test
    fun `approval predicate failure fails closed`() {
        val executed = AtomicBoolean(false)
        LiteRtToolBridgeRegistry.setForRequest(
            listOf(
                Tool(
                    name = "broken_policy",
                    description = "test",
                    needsApproval = { error("policy unavailable") },
                    execute = {
                        executed.set(true)
                        listOf(UIMessagePart.Text("executed"))
                    },
                )
            )
        )

        val result = LiteRtToolBridge().runTool("broken_policy", buildJsonObject {}.toString())

        assertFalse(executed.get())
        assertTrue(result.contains("\"error\":\"approval_check_failed\""))
    }
}
