package me.rerere.agentruntime

import com.google.adk.kt.tools.BaseTool

/**
 * Model capability tier. Drives how many and which tools a model sees.
 * LOCAL_SMALL (e.g. Gemma-class on-device): narrow budget, core tools only.
 * LOCAL_LARGE: a larger budget, still bounded.
 * CLOUD (e.g. GPT/Claude/Gemini): unbounded — the full tool surface.
 */
enum class ModelTier { LOCAL_SMALL, LOCAL_LARGE, CLOUD }

/**
 * Capability profile of the model driving the agent. [toolBudget] overrides the
 * tier's default budget when set.
 */
data class ToolCapabilities(val tier: ModelTier, val toolBudget: Int? = null)

/**
 * Capability-aware tool selection. Coday's tools are selected per-agent by an
 * explicit `integrations` map (or ALL when undefined) — never by model
 * capability. This is the Meristem one-step-beyond: a small local model should
 * not be handed 70 giant tool schemas, so [filter] keeps the runtime's core
 * tools ([PRIORITY_TOOL_NAMES]) and fills the remaining budget with the rest in
 * declaration order. CLOUD tier (budget null) is unbounded.
 */
object ToolFilter {

    /** Runtime-owned tools that are always kept, even under a tiny budget. */
    val PRIORITY_TOOL_NAMES = setOf("delegate", "proposeMemory")

    fun filter(tools: List<BaseTool>, capabilities: ToolCapabilities, budget: Int? = null): List<BaseTool> {
        val effectiveBudget = budget ?: capabilities.toolBudget ?: when (capabilities.tier) {
            ModelTier.LOCAL_SMALL -> 8
            ModelTier.LOCAL_LARGE -> 24
            ModelTier.CLOUD -> return tools
        }
        val priority = tools.filter { it.name in PRIORITY_TOOL_NAMES }
        val rest = tools.filter { it.name !in PRIORITY_TOOL_NAMES }
        val remaining = (effectiveBudget - priority.size).coerceAtLeast(0)
        return priority + rest.take(remaining)
    }
}
