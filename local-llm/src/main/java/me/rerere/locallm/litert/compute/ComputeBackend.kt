package me.rerere.locallm.litert.compute

interface ComputeBackend {
    // Adapter surface for host runtimes (LiteRT, Stable Diffusion, GPU, remote).
    // Implementations drive ComputeSession.dispatch + observeXxx from native events.
}
