package me.rerere.agentruntime

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import kotlinx.coroutines.flow.Flow

/**
 * How much context a delegated specialist should receive.
 *
 * Meristem principle: give a specialist the smallest context that lets it do the job
 * correctly. Never mutilate canonical history because a model has a context limit —
 * the parent keeps its full history; the policy decides what the *specialist* sees.
 */
sealed interface ContextPolicy {
    /** Whole parent history. */
    data object Full : ContextPolicy

    /** The current conversation branch only. */
    data object CurrentBranch : ContextPolicy

    /** Only the latest user turn. */
    data object CurrentTurn : ContextPolicy

    /** No conversation context at all — the task must be self-contained. */
    data object ProjectOnly : ContextPolicy

    /** Caller-supplied, fully self-contained task package. */
    data class Explicit(
        val background: String? = null,
        val requirements: List<String> = emptyList(),
        val constraints: List<String> = emptyList(),
        val deliverables: List<String> = emptyList(),
        val references: List<String> = emptyList(),
        val definitionOfDone: List<String> = emptyList(),
    ) : ContextPolicy

    /** Narrow, relevant context only. */
    data class Narrow(
        val files: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val conversation: ConversationScope = ConversationScope.CurrentTurn,
    ) : ContextPolicy

    enum class ConversationScope { None, CurrentTurn, Full }
}

/** Raw parent conversation, oldest first. */
data class ParentContext(
    val messages: List<String> = emptyList(),
)

/** Builds a self-contained task package for a delegated specialist. */
object DelegationTaskBuilder {

    private fun header(title: String) = "\n[${title.uppercase()}]\n"

    fun build(task: String, policy: ContextPolicy, parent: ParentContext): String = buildString {
        append("TASK\n")
        append(task)
        when (policy) {
            is ContextPolicy.ProjectOnly -> Unit
            is ContextPolicy.Explicit -> {
                policy.background?.let { append(header("background")); append(it) }
                appendList(policy.requirements, "requirements")
                appendList(policy.constraints, "constraints")
                appendList(policy.deliverables, "deliverables")
                appendList(policy.references, "references")
                appendList(policy.definitionOfDone, "definition of done")
            }

            is ContextPolicy.Narrow -> {
                appendList(policy.files, "files")
                appendList(policy.decisions, "decisions")
                when (policy.conversation) {
                    ContextPolicy.ConversationScope.None -> Unit
                    ContextPolicy.ConversationScope.CurrentTurn -> appendConversation(parent.messages.takeLast(1))
                    ContextPolicy.ConversationScope.Full -> appendConversation(parent.messages)
                }
            }

            ContextPolicy.CurrentTurn -> appendConversation(parent.messages.takeLast(1))
            ContextPolicy.CurrentBranch, ContextPolicy.Full -> appendConversation(parent.messages)
        }
    }

    private fun StringBuilder.appendList(items: List<String>, title: String) {
        if (items.isEmpty()) return
        append(header(title))
        items.forEach { append("- "); append(it); append("\n") }
    }

    private fun StringBuilder.appendConversation(messages: List<String>) {
        if (messages.isEmpty()) return
        append(header("conversation"))
        messages.forEach { append(it); append("\n") }
    }
}

/** Bounded recursion depth for delegation. */
class DelegationDepth(val max: Int) {
    private var current = 0

    fun tryEnter(): Boolean {
        if (current >= max) return false
        current++
        return true
    }

    fun exit() {
        current--
    }
}

data class DelegationResult(
    val agentName: String,
    val text: String,
)

/**
 * Forks the specialist with a clean context: the built task package becomes the
 * specialist's entire input, and only the result text returns to the caller.
 * Depth is bounded by [depth] so recursive delegation cannot grow unbounded.
 */
suspend fun delegate(
    runtime: AgentRuntime,
    agent: AssistantDefinition,
    task: String,
    context: ContextPolicy,
    parent: ParentContext = ParentContext(),
    depth: DelegationDepth,
): DelegationResult {
    if (!depth.tryEnter()) {
        throw IllegalStateException("Delegation depth exhausted (max ${depth.max})")
    }
    try {
        val taskPackage = DelegationTaskBuilder.build(task, context, parent)
        val text = buildString {
            runtime.run(agent, taskPackage).collect { event ->
                if (event is AgentEvent.Text) append(event.text)
            }
        }
        return DelegationResult(agentName = agent.name, text = text)
    } finally {
        depth.exit()
    }
}

/**
 * Resolves a named sub-agent and delegates a self-contained task to it in an
 * isolated runtime. The task package becomes the specialist's entire context and
 * only the result text returns. Returns a JSON-native tool result: an error map
 * when the agent is unknown, otherwise the specialist's reply.
 */
suspend fun delegateToSubAgent(
    subAgents: Map<String, AssistantDefinition>,
    depth: DelegationDepth,
    runtimeFactory: (userId: String) -> AgentRuntime,
    agentName: String,
    task: String,
    context: ContextPolicy = ContextPolicy.ProjectOnly,
    parent: ParentContext = ParentContext(),
): Any {
    val agent = subAgents[agentName]
        ?: return mapOf("error" to "No sub-agent named '$agentName'")
    val runtime = runtimeFactory("rikkahub-user")
    val result = delegate(runtime = runtime, agent = agent, task = task, context = context, parent = parent, depth = depth)
    return mapOf("result" to result.text)
}

/**
 * An ADK tool that exposes sub-agent delegation to a parent agent. The parent
 * calls it with `agent` (the specialist's name) and `task` (a fully self-contained
 * description); the specialist runs with clean context and returns only its final
 * text as the tool result.
 */
class DelegateTool(
    private val subAgents: Map<String, AssistantDefinition>,
    private val depth: DelegationDepth,
    private val runtimeFactory: (userId: String) -> AgentRuntime,
) : FunctionTool(
    name = "delegate",
    description = "Delegates a fully self-contained task to a specialist sub-agent. " +
        "The task must include all context, constraints, and expected deliverables — " +
        "the specialist sees nothing else. Returns only the specialist's final text. " +
        "Args: agent (one of ${subAgents.keys.sorted()}), task (self-contained description).",
) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val agentName = args["agent"] as? String ?: return mapOf("error" to "Missing 'agent' argument")
        val task = args["task"] as? String ?: return mapOf("error" to "Missing 'task' argument")
        return delegateToSubAgent(
            subAgents = subAgents,
            depth = depth,
            runtimeFactory = runtimeFactory,
            agentName = agentName,
            task = task,
        )
    }
}
