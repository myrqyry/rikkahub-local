package me.rerere.locallm.litert.compute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComputeCommand {
    @Serializable
    @SerialName("compute_load")
    data class Load(
        val modelId: String,
        val requirements: ComputeRequirements,
    ) : ComputeCommand()

    @Serializable
    @SerialName("compute_execute")
    data class Execute(
        val modelId: String,
        val operation: String,
        val input: Map<String, String> = emptyMap(),
        val requirements: ComputeRequirements,
    ) : ComputeCommand()

    @Serializable
    @SerialName("compute_release")
    data class Release(val modelId: String) : ComputeCommand()

    @Serializable
    @SerialName("compute_shutdown")
    data object Shutdown : ComputeCommand()
}
