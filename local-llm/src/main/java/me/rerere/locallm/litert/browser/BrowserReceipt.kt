package me.rerere.locallm.litert.browser

import kotlinx.serialization.Serializable

/**
 * Bounded durable summary of a browser session. [commands] holds the simple
 * type name of every dispatched command in order; [refusals] holds each
 * refusal reason in order. Never carries full page payloads.
 */
@Serializable
data class BrowserReceipt(
    val session: BrowserRef,
    val commands: List<String>,
    val effects: Set<BrowserEffect>,
    val refusals: List<String>,
    val observationCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val terminalState: String,
    val error: String? = null,
)

/** Build a receipt for a session's run. The session must track its own ledger. */
fun BrowserSession.buildReceipt(startedAtMs: Long, error: String? = null): BrowserReceipt =
    BrowserReceipt(
        session = ref,
        commands = ledgerCommands.toList(),
        effects = ledgerEffects.toSet(),
        refusals = ledgerRefusals.toList(),
        observationCount = ledgerCommands.size,
        startedAtMs = startedAtMs,
        completedAtMs = null,
        terminalState = state.name,
        error = error,
    )
