package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

enum class AcceleratorPreference { AUTO, CPU, GPU, NPU, QNN, NNAPI }

@Serializable
data class ComputeRequirements(
    val accelerator: AcceleratorPreference,
    val estimatedModelBytes: Long,
    val maxCpuMillis: Long,
    val maxGpuMillis: Long,
    val maxAcceleratorMemoryBytes: Long,
)
