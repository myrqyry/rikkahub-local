package me.rerere.rikkahub.ui.pages.models

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val MEMORY_SAMPLE_INTERVAL_MILLIS = 5_000L

internal suspend fun sampleAvailableMemory(
    readAvailableMemory: () -> Long,
    delayFor: suspend (Long) -> Unit = { delay(it) },
    emit: (Long) -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        emit(readAvailableMemory())
        delayFor(MEMORY_SAMPLE_INTERVAL_MILLIS)
    }
}
