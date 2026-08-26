package me.rerere.rikkahub.ui.pages.models

import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.MemoryGuard
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource

sealed interface ModelMemoryFit {
    data object Checking : ModelMemoryFit
    data object Unavailable : ModelMemoryFit
    data class FitsNow(val modelFileBytes: Long, val availMemBytes: Long) : ModelMemoryFit
    data class NeedsMoreMemory(
        val modelFileBytes: Long,
        val availMemBytes: Long,
        val requiredFreeBytes: Long,
    ) : ModelMemoryFit
}

fun ModelDescriptor.memoryFit(availMemBytes: Long?): ModelMemoryFit {
    val local = source as? ModelSource.Local ?: return ModelMemoryFit.Unavailable
    if (local.runtime != LocalRuntime.LiteRT || lifecycle != ModelLifecycle.READY || !installed) {
        return ModelMemoryFit.Unavailable
    }
    val modelFileBytes = metadata["sizeBytes"]?.toLongOrNull()?.takeIf { it > 0 }
        ?: return ModelMemoryFit.Unavailable
    if (availMemBytes == null) return ModelMemoryFit.Checking

    return when (val decision = MemoryGuard.decide(modelFileBytes, availMemBytes)) {
        MemoryGuard.Decision.Ok -> ModelMemoryFit.FitsNow(modelFileBytes, availMemBytes)
        is MemoryGuard.Decision.TooLarge -> ModelMemoryFit.NeedsMoreMemory(
            modelFileBytes = decision.modelFileBytes,
            availMemBytes = decision.availMemBytes,
            requiredFreeBytes = decision.requiredFreeBytes,
        )
    }
}
