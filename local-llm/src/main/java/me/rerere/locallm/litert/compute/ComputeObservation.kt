package me.rerere.locallm.litert.compute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComputeObservation {
    @Serializable
    @SerialName("compute_loaded")
    data class Loaded(val modelId: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_started")
    data class ExecutionStarted(val modelId: String, val operation: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_completed")
    data class ExecutionCompleted(val modelId: String, val operation: String, val outputBytes: Long) : ComputeObservation()

    @Serializable
    @SerialName("compute_execution_failed")
    data class ExecutionFailed(val modelId: String, val operation: String, val detail: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_released")
    data class Released(val modelId: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_shutdown_complete")
    data object ShutdownComplete : ComputeObservation()

    @Serializable
    @SerialName("compute_evicted")
    data class Evicted(val reason: String) : ComputeObservation()

    @Serializable
    @SerialName("compute_command_refused")
    data class CommandRefused(val reason: String) : ComputeObservation()
}
