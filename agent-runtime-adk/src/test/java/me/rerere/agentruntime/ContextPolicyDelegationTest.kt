package me.rerere.agentruntime

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model as AdkModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPolicyDelegationTest {

    private class FakeAdkModel(override val name: String) : AdkModel {
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = emptyFlow()
    }

    private class FakeRuntime(vararg replies: AgentEvent) : AgentRuntime {
        val events = replies.toList()
        var lastInput: String? = null
        var lastAssistant: AssistantDefinition? = null
        override val agentName = "fake-runtime"
        override fun run(assistant: AssistantDefinition, input: String): Flow<AgentEvent> {
            lastInput = input
            lastAssistant = assistant
            return events.asFlow()
        }
    }

    private val specialist = AssistantDefinition(name = "Reviewer", model = FakeAdkModel("local/qwen"))

    private val parent = ParentContext(
        messages = listOf(
            "User: how does routing work?",
            "Assistant: it maps intents to handlers.",
        ),
    )

    @Test
    fun `all six context policies exist and are distinct`() {
        val policies = listOf<ContextPolicy>(
            ContextPolicy.Full,
            ContextPolicy.CurrentBranch,
            ContextPolicy.CurrentTurn,
            ContextPolicy.ProjectOnly,
            ContextPolicy.Explicit(),
            ContextPolicy.Narrow(),
        )
        assertEquals(6, policies.distinct().size)
    }

    @Test
    fun `ProjectOnly task package carries the task but no conversation`() {
        val packageText = DelegationTaskBuilder.build(
            task = "review the routing change",
            policy = ContextPolicy.ProjectOnly,
            parent = parent,
        )
        assertTrue(packageText.contains("review the routing change"))
        assertFalse(packageText.contains("how does routing work"))
    }

    @Test
    fun `Full task package carries the whole parent conversation`() {
        val packageText = DelegationTaskBuilder.build(
            task = "review the routing change",
            policy = ContextPolicy.Full,
            parent = parent,
        )
        assertTrue(packageText.contains("review the routing change"))
        assertTrue(packageText.contains("how does routing work"))
        assertTrue(packageText.contains("it maps intents to handlers"))
    }

    @Test
    fun `CurrentTurn task package carries only the last parent message`() {
        val packageText = DelegationTaskBuilder.build(
            task = "answer the last question",
            policy = ContextPolicy.CurrentTurn,
            parent = parent,
        )
        assertTrue(packageText.contains("answer the last question"))
        assertTrue(packageText.contains("it maps intents to handlers"))
        assertFalse(packageText.contains("how does routing work"))
    }

    @Test
    fun `Narrow task package lists files and decisions`() {
        val packageText = DelegationTaskBuilder.build(
            task = "review the routing change",
            policy = ContextPolicy.Narrow(
                files = listOf("app/Route.kt"),
                decisions = listOf("keep streaming on the main thread"),
            ),
            parent = parent,
        )
        assertTrue(packageText.contains("app/Route.kt"))
        assertTrue(packageText.contains("keep streaming on the main thread"))
    }

    @Test
    fun `Explicit task package renders the self-contained sections`() {
        val packageText = DelegationTaskBuilder.build(
            task = "fix the crash",
            policy = ContextPolicy.Explicit(
                background = "the app crashes on cold start",
                requirements = listOf("reproduce in one minute"),
                constraints = listOf("do not touch the network layer"),
                deliverables = listOf("a patch"),
                definitionOfDone = listOf("all unit tests pass"),
            ),
            parent = parent,
        )
        assertTrue(packageText.contains("the app crashes on cold start"))
        assertTrue(packageText.contains("reproduce in one minute"))
        assertTrue(packageText.contains("do not touch the network layer"))
        assertTrue(packageText.contains("a patch"))
        assertTrue(packageText.contains("all unit tests pass"))
    }

    @Test
    fun `delegate runs the specialist and returns only its final text`() = runBlocking {
        val runtime = FakeRuntime(AgentEvent.Text("looks good to me", partial = true))
        val result = delegate(
            runtime = runtime,
            agent = specialist,
            task = "review the routing change",
            context = ContextPolicy.ProjectOnly,
            depth = DelegationDepth(max = 3),
        )
        assertEquals("Reviewer", result.agentName)
        assertEquals("looks good to me", result.text)
    }

    @Test
    fun `delegate is clean-context - specialist receives the built task package`() = runBlocking {
        val runtime = FakeRuntime(AgentEvent.Text("done"))
        delegate(
            runtime = runtime,
            agent = specialist,
            task = "review the routing change",
            context = ContextPolicy.ProjectOnly,
            parent = parent,
            depth = DelegationDepth(max = 3),
        )
        assertEquals(specialist.name, runtime.lastAssistant?.name)
        assertTrue(runtime.lastInput.orEmpty().contains("review the routing change"))
        assertFalse(runtime.lastInput.orEmpty().contains("how does routing work"))
    }

    @Test
    fun `depth budget bounds nested delegation`() = runBlocking {
        val depth = DelegationDepth(max = 1)
        assertTrue(depth.tryEnter())
        assertFalse(depth.tryEnter())
        depth.exit()
        assertTrue(depth.tryEnter())
        depth.exit()

        val exhausted = DelegationDepth(max = 1)
        assertTrue(exhausted.tryEnter())
        try {
            delegate(
                runtime = FakeRuntime(AgentEvent.Text("no")),
                agent = specialist,
                task = "over budget",
                context = ContextPolicy.ProjectOnly,
                depth = exhausted,
            )
            assertTrue("expected IllegalStateException when depth is exhausted", false)
        } catch (expected: IllegalStateException) {
        } finally {
            exhausted.exit()
        }
    }
}
