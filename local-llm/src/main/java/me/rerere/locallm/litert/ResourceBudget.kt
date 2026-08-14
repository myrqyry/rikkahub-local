package me.rerere.locallm.litert

import kotlinx.serialization.Serializable

/**
 * Operation-level resource budget: what a single operation is allowed to consume.
 *
 * This is deliberately distinct from runtime memory partitioning (how a runtime divides
 * its own memory/storage tiers). `ResourceBudget` bounds the *operation*; the runtime's
 * own tiering is a separate concern that stays out of this type.
 */
@Serializable
data class ResourceBudget(
    val maxDurationMs: Long? = null,
    val maxNetworkRequests: Int? = null,
    val maxDownloadBytes: Long? = null,
    val maxWriteBytes: Long? = null,
    val maxOutputBytes: Long? = null,
    val maxToolCalls: Int? = null,
    val maxConcurrency: Int? = null,
    val compute: ComputeBudget? = null,
) {
    /** True when no bound is set at all — the operation is unbounded. */
    fun isUnbounded(): Boolean = maxDurationMs == null &&
        maxNetworkRequests == null && maxDownloadBytes == null && maxWriteBytes == null &&
        maxOutputBytes == null && maxToolCalls == null && maxConcurrency == null && compute == null

    /** True when every configured bound is respected by the provided usage snapshot. */
    fun respects(usage: ResourceUsage): Boolean {
        if (maxDurationMs != null && usage.elapsedMs != null && usage.elapsedMs > maxDurationMs) return false
        if (maxNetworkRequests != null && usage.networkRequests != null && usage.networkRequests > maxNetworkRequests) return false
        if (maxDownloadBytes != null && usage.downloadBytes != null && usage.downloadBytes > maxDownloadBytes) return false
        if (maxWriteBytes != null && usage.writeBytes != null && usage.writeBytes > maxWriteBytes) return false
        if (maxOutputBytes != null && usage.outputBytes != null && usage.outputBytes > maxOutputBytes) return false
        if (maxToolCalls != null && usage.toolCalls != null && usage.toolCalls > maxToolCalls) return false
        if (maxConcurrency != null && usage.concurrency != null && usage.concurrency > maxConcurrency) return false
        if (compute != null && usage.compute != null && !compute.respects(usage.compute)) return false
        return true
    }
}

/** Snapshot of resources actually consumed by an operation, for budget enforcement. */
@Serializable
data class ResourceUsage(
    val elapsedMs: Long? = null,
    val networkRequests: Int? = null,
    val downloadBytes: Long? = null,
    val writeBytes: Long? = null,
    val outputBytes: Long? = null,
    val toolCalls: Int? = null,
    val concurrency: Int? = null,
    val compute: ComputeUsage? = null,
)

/** Compute-specific budget for an operation. */
@Serializable
data class ComputeBudget(
    val maxCpuMillis: Long? = null,
    val maxGpuMillis: Long? = null,
    val maxAcceleratorMemoryBytes: Long? = null,
    val maxCost: Double? = null,
) {
    fun respects(usage: ComputeUsage): Boolean {
        if (maxCpuMillis != null && usage.cpuMillis != null && usage.cpuMillis > maxCpuMillis) return false
        if (maxGpuMillis != null && usage.gpuMillis != null && usage.gpuMillis > maxGpuMillis) return false
        if (maxAcceleratorMemoryBytes != null && usage.acceleratorMemoryBytes != null && usage.acceleratorMemoryBytes > maxAcceleratorMemoryBytes) return false
        if (maxCost != null && usage.cost != null && usage.cost > maxCost) return false
        return true
    }
}

/** Snapshot of compute resources actually consumed. */
@Serializable
data class ComputeUsage(
    val cpuMillis: Long? = null,
    val gpuMillis: Long? = null,
    val acceleratorMemoryBytes: Long? = null,
    val cost: Double? = null,
)
