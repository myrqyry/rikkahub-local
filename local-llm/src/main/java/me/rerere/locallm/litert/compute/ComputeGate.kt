package me.rerere.locallm.litert.compute

import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.MemoryGuard
import me.rerere.locallm.litert.CapabilityGrant

/**
 * Static, pure capability and resource policy for compute commands.
 *
 * Compute describes execution and resource constraints; ServiceWorld describes
 * execution environment fidelity. This gate only governs the former.
 */
class ComputeGate {

    val memoryDeniedReason = "compute_memory_denied"
    val budgetInvalidReason = "compute_budget_invalid"
    val acceleratorUnknownReason = "compute_accelerator_unknown"
    val executeDeniedReason = "compute_execute_denied"

    fun evaluate(
        command: ComputeCommand,
        granted: CapabilityGrant?,
        capabilities: AcceleratorProbe.LiteRtCapabilities?,
        availMemBytes: Long,
    ): ComputeDecision = when (command) {
        is ComputeCommand.Load -> evaluateLoad(command, capabilities, availMemBytes)
        is ComputeCommand.Execute -> evaluateExecute(command, granted, capabilities)
        is ComputeCommand.Release -> ComputeDecision(true, effect = ComputeEffect.RELEASE)
        ComputeCommand.Shutdown -> ComputeDecision(true, effect = ComputeEffect.SHUTDOWN)
    }

    fun resolveAccelerator(requirements: ComputeRequirements, capabilities: AcceleratorProbe.LiteRtCapabilities?): String? =
        when (requirements.accelerator) {
            AcceleratorPreference.AUTO -> {
                val caps = capabilities ?: return null
                (AcceleratorProbe.pickLiteRt(caps) ?: AcceleratorProbe.pickTaskAccelerator(caps)).lowercase()
            }
            AcceleratorPreference.CPU -> "cpu"
            AcceleratorPreference.GPU -> "gpu"
            AcceleratorPreference.NPU -> "npu"
            AcceleratorPreference.QNN -> "qnn"
            AcceleratorPreference.NNAPI -> "nnapi"
        }

    private fun evaluateLoad(
        command: ComputeCommand.Load,
        capabilities: AcceleratorProbe.LiteRtCapabilities?,
        availMemBytes: Long,
    ): ComputeDecision {
        val estimatedModelBytes = command.requirements.estimatedModelBytes
        if (estimatedModelBytes > 0 && availMemBytes > 0) {
            val decision = MemoryGuard.decide(estimatedModelBytes, availMemBytes)
            if (decision is MemoryGuard.Decision.TooLarge) {
                return ComputeDecision(false, memoryDeniedReason)
            }
        }
        return ComputeDecision(true, effect = ComputeEffect.LOAD)
    }

    private fun evaluateExecute(
        command: ComputeCommand.Execute,
        granted: CapabilityGrant?,
        capabilities: AcceleratorProbe.LiteRtCapabilities?,
    ): ComputeDecision {
        val r = command.requirements
        val budgetValid = r.maxCpuMillis >= 0 && r.maxGpuMillis >= 0 && r.maxAcceleratorMemoryBytes >= 0
        if (!budgetValid) {
            return ComputeDecision(false, budgetInvalidReason)
        }
        if (resolveAccelerator(r, capabilities) == null) {
            return ComputeDecision(false, acceleratorUnknownReason)
        }
        if (granted != null && granted.grantedCapabilities.isNotEmpty() && !granted.isAllowed("compute_execute")) {
            return ComputeDecision(false, executeDeniedReason)
        }
        return ComputeDecision(true, effect = ComputeEffect.EXECUTE)
    }
}
