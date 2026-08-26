package me.rerere.rikkahub.ui.pages.models

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMemorySamplerTest {
    @Test
    fun `samples immediately then every five seconds until canceled`() = runBlocking {
        val samples = mutableListOf<Long>()
        val waits = mutableListOf<Long>()
        var nextMemory = 100L

        val job = launch {
            sampleAvailableMemory(
                readAvailableMemory = { nextMemory++ },
                delayFor = { delayMillis ->
                    waits += delayMillis
                    if (waits.size == 2) currentCoroutineContext().cancel()
                },
                emit = samples::add,
            )
        }
        job.join()

        assertEquals(listOf(100L, 101L), samples)
        assertEquals(listOf(5_000L, 5_000L), waits)
    }
}
