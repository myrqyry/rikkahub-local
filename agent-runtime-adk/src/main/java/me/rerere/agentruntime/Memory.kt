package me.rerere.agentruntime

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Meristem-native memory layer (Coday-inspired, with the one-step-beyond):
 * the agent proposes memories; nothing is written until the user accepts.
 */
enum class MemoryLevel { USER, PROJECT }

data class Memory(val title: String, val content: String, val level: MemoryLevel)

data class MemoryProposal(val id: Uuid, val memory: Memory)

/**
 * Pending proposals + committed memories. Committed entries are keyed by
 * (level, title) and upserted on accept — same as Coday's upsertMemory,
 * except the write only happens after explicit user acceptance.
 */
@OptIn(ExperimentalUuidApi::class)
class MemoryStore {
    private val pending = LinkedHashMap<Uuid, MemoryProposal>()
    private val committed = LinkedHashMap<String, Memory>()

    fun propose(memory: Memory): MemoryProposal {
        pending.values.removeAll { it.memory.level == memory.level && it.memory.title == memory.title }
        val proposal = MemoryProposal(id = Uuid.random(), memory = memory)
        pending[proposal.id] = proposal
        return proposal
    }

    fun pendingProposals(): List<MemoryProposal> = pending.values.toList()

    fun accept(id: Uuid): Memory? {
        val proposal = pending.remove(id) ?: return null
        committed[key(proposal.memory.level, proposal.memory.title)] = proposal.memory
        return proposal.memory
    }

    fun reject(id: Uuid): Boolean = pending.remove(id) != null

    fun listMemories(level: MemoryLevel? = null): List<Memory>? {
        val result = if (level == null) committed.values.toList() else committed.values.filter { it.level == level }
        return result.ifEmpty { null }
    }

    fun readMemory(title: String): Memory? = committed.values.firstOrNull { it.title == title }

    private fun key(level: MemoryLevel, title: String): String = "$level:$title"
}

object MemoryTools {
    /**
     * Pure helper the [proposeMemory] FunctionTool delegates to. Mirrors
     * Coday's memorizeProject/memorizeUser naming but gates the write behind
     * a pending proposal instead of upserting immediately.
     */
    fun proposeMemory(store: MemoryStore, agentName: String, title: String, content: String, level: String): String {
        val memoryLevel = when (level.lowercase()) {
            "project" -> MemoryLevel.PROJECT
            else -> MemoryLevel.USER
        }
        val proposal = store.propose(Memory(title = title, content = content, level = memoryLevel))
        return "$agentName: memory proposed for $memoryLevel level, pending user acceptance: $title (${proposal.id})"
    }
}

/**
 * ADK [FunctionTool] exposing memory proposals to the agent. Mirrors Coday's
 * memorizeProject/memorizeUser naming but gates writes: nothing is committed
 * until the host accepts the proposal via [MemoryStore.accept]. The proposal id
 * is returned in the tool result so the host can present accept/reject.
 */
class ProposeMemoryTool(
    private val store: MemoryStore,
    private val agentName: String,
) : FunctionTool(
    name = "proposeMemory",
    description = "Upsert a MEMORY PROPOSAL (title + content). The memory is NOT written until the user accepts it. " +
        "The title should be precise, unique, and reflect the full scope. The content must be complete, validated " +
        "knowledge, self-contained but not redundant with existing memories. Nothing persists without acceptance.",
) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(name = name, description = description)

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        proposeMemoryToolRun(store = store, agentName = agentName, args = args)
}

/**
 * Package-private pure implementation of [ProposeMemoryTool.execute] — kept free of
 * the ADK [ToolContext] so it is JVM-unit-testable without an InvocationContext.
 */
fun proposeMemoryToolRun(store: MemoryStore, agentName: String, args: Map<String, Any?>): Any {
    val title = args["title"] as? String ?: return mapOf("error" to "Missing 'title' argument")
    val content = args["content"] as? String ?: return mapOf("error" to "Missing 'content' argument")
    val level = args["level"] as? String ?: "user"
    val result = MemoryTools.proposeMemory(store = store, agentName = agentName, title = title, content = content, level = level)
    return mapOf("result" to result)
}
