package me.rerere.locallm.litert.compute

import me.rerere.locallm.litert.CapabilityGrant

/**
 * Static, pure capability and resource policy for compute commands.
 *
 * Compute describes execution and resource constraints; ServiceWorld describes
 * execution environment fidelity. This gate only governs the former. All inputs
 * ([ComputeCapabilities], [MemoryAdmission]) are pure — Android adapters supply
 * them, so the seam stays JVM-portable.
 */
class ComputeGate {

    val memoryDeniedReason = "compute_memory_denied"
    val budgetInvalidReason = "compute_budget_invalid"
    val acceleratorUnknownReason = "compute_accelerator_unknown"
    val executeDeniedReason = "compute_execute_denied"

    fun evaluate(
        command: ComputeCommand,
        granted: CapabilityGrant?,
        capabilities: ComputeCapabilities?,
        availMemBytes: Long,
    ): ComputeDecision = when (command) {
        is ComputeCommand.Load -> evaluateLoad(command, availMemBytes)
        is ComputeCommand.Execute -> evaluateExecute(command, granted, capabilities)
        is ComputeCommand.Release -> ComputeDecision(true, effect = ComputeEffect.RELEASE)
        ComputeCommand.Shutdown -> ComputeDecision(true, effect = ComputeEffect.SHUTDOWN)
    }

    fun resolveAccelerator(requirements: ComputeRequirements, capabilities: ComputeCapabilities?): String? =
        when (requirements.accelerator) {
            AcceleratorPreference.AUTO -> {
                val caps = capabilities ?: return null
                pickLiteRt(caps).lowercase()
            }
            AcceleratorPreference.CPU -> "cpu"
            AcceleratorPreference.GPU ->
                resolveExplicit("gpu", capabilities) { it.gpuDelegateSupported }
            AcceleratorPreference.NPU ->
                resolveExplicit("npu", capabilities) { it.npuSupported }
            AcceleratorPreference.QNN ->
                resolveExplicit("qnn", capabilities) { it.isQualcomm && it.qnnLibrarySupported }
            AcceleratorPreference.NNAPI ->
                resolveExplicit("nnapi", capabilities) { it.nnapiSupported }
        }

    /**
     * An explicit accelerator preference is honoured only when the capability
     * snapshot backs it. With no snapshot (unprobed) the request is taken at face
     * value; with a snapshot that shows the accelerator unsupported, fall back to
     * the best available pick so execution can still proceed.
     */
    private fun resolveExplicit(
        name: String,
        capabilities: ComputeCapabilities?,
        supported: (ComputeCapabilities) -> Boolean,
    ): String {
        if (capabilities == null) return name
        return if (supported(capabilities)) name else pickLiteRt(capabilities).lowercase()
    }

    private fun evaluateLoad(
        command: ComputeCommand.Load,
        availMemBytes: Long,
    ): ComputeDecision {
        val estimatedModelBytes = command.requirements.estimatedModelBytes
        if (estimatedModelBytes > 0 && availMemBytes > 0) {
            val decision = decideMemoryAdmission(estimatedModelBytes, availMemBytes)
            if (decision is MemoryAdmission.TooLarge) {
                return ComputeDecision(false, memoryDeniedReason)
            }
        }
        return ComputeDecision(true, effect = ComputeEffect.LOAD)
    }

    private fun evaluateExecute(
        command: ComputeCommand.Execute,
        granted: CapabilityGrant?,
        capabilities: ComputeCapabilities?,
    ): ComputeDecision {
        val r = command.requirements
        val budgetValid = r.maxCpuMillis >= 0 && r.maxGpuMillis >= 0 && r.maxAcceleratorMemoryBytes >= 0
        if (!budgetValid) {
            return ComputeDecision(false, budgetInvalidReason)
        }
        if (resolveAccelerator(r, capabilities) == null) {
            return ComputeDecision(false, acceleratorUnknownReason)
        }
        // A non-null grant means the user explicitly enumerated capabilities —
        // an empty list grants nothing. Only a null grant is the unscoped/backward
        // compatible case that allows execution.
        if (granted != null && !granted.isAllowed("compute_execute")) {
            return ComputeDecision(false, executeDeniedReason)
        }
        return ComputeDecision(true, effect = ComputeEffect.EXECUTE)
    }
}
