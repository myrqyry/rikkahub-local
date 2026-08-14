package me.rerere.locallm.litert.terminal

import kotlinx.serialization.Serializable

/**
 * Phase F (roadmap F8). A bounded summary of a completed process run, persisted to AgentRun
 * durable history. Raw output never lives here — it stays in scrollback / an artifact;
 * [outputBytes] / [outputTruncated] give a size signal, and [commandDigest] correlates this
 * receipt back to the exact [ProcessEffectPlan] the gate authorised. An `outputRef` is added
 * when the canonical ArtifactRef lands (roadmap H).
 */
@Serializable
data class ProcessReceipt(
    val process: ProcessRef,
    val command: List<String>,
    val commandDigest: String,
    val effects: Set<String>,
    val reads: List<String>,
    val writes: List<String>,
    val network: Boolean,
    val nativeExecution: Boolean,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val exitCode: Int,
    val termination: String,
    val outputBytes: Long,
    val outputTruncated: Boolean,
)
