package me.rerere.agentruntime

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.Model as AdkModel
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Assistant definition the runtime consumes. Owns assistant config; the runtime owns execution. */
data class AssistantDefinition(
    val name: String,
    val model: AdkModel,
    val systemPrompt: String? = null,
    val tools: List<BaseTool> = emptyList(),
)

/** Agent events surfaced by [AgentRuntime]. */
sealed interface AgentEvent {
    data class Text(val text: String, val partial: Boolean = false) : AgentEvent
    data class ToolCall(val name: String, val args: Map<String, Any?>) : AgentEvent
    data class ToolResult(val name: String, val result: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object TurnComplete : AgentEvent
    data object EndOfAgent : AgentEvent
}

/** The Meristem-owned boundary: Rikkahub owns assistants/conversations/models/tools; runtimes execute. */
interface AgentRuntime {
    val agentName: String

    fun run(assistant: AssistantDefinition, input: String): Flow<AgentEvent>
}

/** Maps an ADK [Event] to an [AgentEvent], or null when the event carries nothing of interest. */
fun Event.toAgentEvent(): AgentEvent? {
    val text = content?.parts?.firstOrNull { it.text != null }?.text
    val functionCall = content?.parts?.firstOrNull { it.functionCall != null }?.functionCall
    val functionResponse = content?.parts?.firstOrNull { it.functionResponse != null }?.functionResponse
    val error = errorMessage
    return when {
        text != null -> AgentEvent.Text(text = text, partial = partial)
        functionCall != null -> AgentEvent.ToolCall(name = functionCall.name, args = functionCall.args)
        functionResponse != null -> AgentEvent.ToolResult(name = functionResponse.name, result = functionResponse.response.toString())
        finishReason != null -> AgentEvent.TurnComplete
        actions.endOfAgent -> AgentEvent.EndOfAgent
        turnComplete -> AgentEvent.TurnComplete
        error != null -> AgentEvent.Error(message = error)
        else -> null
    }
}

/** Executes an [AssistantDefinition] through the ADK Kotlin graph (LlmAgent + InMemoryRunner). */
class SimpleAgentRuntime(
    private val userId: String = "rikkahub-user",
    override val agentName: String = "SimpleAgentRuntime",
    val subAgents: List<AssistantDefinition> = emptyList(),
    val maxDelegationDepth: Int = 2,
) : AgentRuntime {

    @OptIn(ExperimentalUuidApi::class)
    override fun run(assistant: AssistantDefinition, input: String): Flow<AgentEvent> = flow {
        val depth = DelegationDepth(max = maxDelegationDepth)
        val delegateTool = if (subAgents.isEmpty()) {
            null
        } else {
            DelegateTool(
                subAgents = subAgents.associateBy { it.name },
                depth = depth,
                runtimeFactory = { SimpleAgentRuntime(userId = it, agentName = "delegated") },
            )
        }
        val agent = LlmAgent.Builder()
            .name(assistant.name)
            .model(assistant.model)
            .tools(assistant.tools + listOfNotNull(delegateTool))
            .instruction(assistant.systemPrompt.orEmpty())
            .maxSteps(5)
            .build()
        val runner = InMemoryRunner(agent = agent, appName = agentName)
        try {
            runner
                .runAsync(
                    userId = userId,
                    sessionId = Uuid.random().toString(),
                    newMessage = Content.fromText(role = Role.USER, text = input),
                    runConfig = RunConfig(streamingMode = StreamingMode.SSE),
                )
                .collect { event ->
                    event.toAgentEvent()?.let { emit(it) }
                }
        } finally {
            runner.close()
        }
    }
}
