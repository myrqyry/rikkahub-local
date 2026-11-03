package me.rerere.locallm.litert.compute

/**
 * A measured snapshot of the device state relevant to one local-inference decision.
 *
 * This deliberately keeps "what the device looks like now" separate from model policy.
 * Android adapters populate the snapshot; pure planners consume it. Catalog RAM bands are
 * advisory metadata, while [availableMemoryBytes] is the live admission signal.
 */
data class DeviceExecutionProfile(
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val lowMemory: Boolean,
    val capabilities: ComputeCapabilities,
) {
    val availableMemoryRatio: Double
        get() = if (totalMemoryBytes <= 0L) 0.0
        else availableMemoryBytes.toDouble() / totalMemoryBytes.toDouble()
}
