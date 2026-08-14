package me.rerere.locallm.litert.mesh

import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.ResourceBudget

/**
 * Immutable orchestration context (roadmap D9). Agents receive refs to this context; they do
 * not share one mutable map. Fields are references/identities, not the resources themselves.
 *
 * @param runId AgentRun evidence boundary.
 * @param correlationId identity of the whole orchestration.
 * @param conversationId optional conversation ref (branch/revision identity is guarded at
 *   attachment time by AsyncAttachmentGate, not carried here).
 * @param grant capability grant bounding every role in this orchestration.
 * @param budget resource budget bounding every role in this orchestration.
 */
data class MicroAgentContext(
    val runId: String,
    val correlationId: String,
    val conversationId: String?,
    val grant: CapabilityGrant,
    val budget: ResourceBudget,
)
