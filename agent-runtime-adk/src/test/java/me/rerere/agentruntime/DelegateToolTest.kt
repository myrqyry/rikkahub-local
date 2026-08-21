package me.rerere.agentruntime

import com.google.adk.kt.models.Model as AdkModel
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateToolTest {

    private class FakeAdkModel(override val name: String) : AdkModel {
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = emptyFlow()
    }

    private class FakeRuntime(vararg replies: AgentEvent) : AgentRuntime {
        val events = replies.toList()
        var lastInput: String? = null
        override val agentName = "fake-runtime"

        override fun run(assistant: AssistantDefinition, input: String): Flow<AgentEvent> {
            lastInput = input
            return events.asFlow()
        }
    }

    private val specialist = AssistantDefinition(name = "Reviewer", model = FakeAdkModel("local/qwen"))

    @Test
    fun `delegate tool runs the named specialist and returns only its result text`() = runBlocking {
        val result = delegateToSubAgent(
            subAgents = mapOf("Reviewer" to specialist),
            depth = DelegationDepth(max = 2),
            runtimeFactory = { FakeRuntime(AgentEvent.Text("looks good to me", partial = false)) },
            agentName = "Reviewer",
            task = "review the diff",
        )
        assertEquals(mapOf("result" to "looks good to me"), result)
    }

    @Test
    fun `delegate tool returns an error for an unknown agent name`() = runBlocking {
        var created = 0
        val result = delegateToSubAgent(
            subAgents = mapOf("Reviewer" to specialist),
            depth = DelegationDepth(max = 2),
            runtimeFactory = { created++; FakeRuntime() },
            agentName = "Nobody",
            task = "x",
        )
        assertTrue((result as Map<*, *>)["error"].toString().contains("Nobody"))
        assertEquals(0, created)
    }

    @Test
    fun `delegate tool respects the depth budget`() = runBlocking {
        val depth = DelegationDepth(max = 1)
        assertTrue(depth.tryEnter())
        try {
            val threw = runCatching {
                delegateToSubAgent(
                    subAgents = mapOf("Reviewer" to specialist),
                    depth = depth,
                    runtimeFactory = { FakeRuntime(AgentEvent.Text("never", partial = false)) },
                    agentName = "Reviewer",
                    task = "x",
                )
            }
            assertTrue(threw.isFailure)
            assertTrue(threw.exceptionOrNull() is IllegalStateException)
            assertTrue(threw.exceptionOrNull()?.message.orEmpty().contains("Delegation depth exhausted"))
        } finally {
            depth.exit()
        }
    }

    @Test
    fun `simple agent runtime exposes configured subagents`() {
        val runtime = SimpleAgentRuntime(subAgents = listOf(specialist))
        assertEquals(listOf("Reviewer"), runtime.subAgents.map { it.name })
        assertEquals(2, runtime.maxDelegationDepth)
    }
}
