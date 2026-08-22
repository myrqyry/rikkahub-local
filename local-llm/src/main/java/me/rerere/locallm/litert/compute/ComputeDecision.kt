package me.rerere.locallm.litert.compute

data class ComputeDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val effect: ComputeEffect? = null,
)
