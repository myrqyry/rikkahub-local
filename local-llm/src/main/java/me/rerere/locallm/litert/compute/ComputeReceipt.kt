package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

@Serializable
data class ComputeReceipt(
    val session: ComputeRef,
    val commands: List<String>,
    val effects: Set<ComputeEffect>,
    val refusals: List<String>,
    val observationCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val terminalState: String,
    val error: String? = null,
)

fun ComputeSession.buildReceipt(startedAtMs: Long, error: String? = null): ComputeReceipt = ComputeReceipt(
    session = ref,
    commands = commandsLedger.toList(),
    effects = effectsLedger.toSet(),
    refusals = refusalsLedger.toList(),
    observationCount = commandsLedger.size,
    startedAtMs = startedAtMs,
    completedAtMs = null,
    terminalState = state.name,
    error = error,
)
