package me.rerere.locallm.litert.compute

import kotlinx.serialization.Serializable

@Serializable
data class ComputeRef(val id: String) {
    override fun toString(): String = "compute:$id"
}
