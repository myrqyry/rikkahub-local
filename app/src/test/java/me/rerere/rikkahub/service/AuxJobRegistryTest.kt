package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AuxJobRegistryTest {
    @Test
    fun `new title cancels an older title for the same conversation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = AuxJobRegistry()
        val id = Uuid.random()
        try {
            val first = registry.launch(scope, id, AuxJobKind.TITLE) { awaitCancellation() }
            val second = registry.launch(scope, id, AuxJobKind.TITLE) { awaitCancellation() }
            assertTrue(first.isCancelled)
            assertFalse(second.isCancelled)
            assertTrue(registry.isTracked(id, AuxJobKind.TITLE))
        } finally { scope.cancel() }
    }

    @Test
    fun `title and suggestion can coexist but stop cancels both`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = AuxJobRegistry()
        val id = Uuid.random()
        try {
            val title = registry.launch(scope, id, AuxJobKind.TITLE) { awaitCancellation() }
            val suggestion = registry.launch(scope, id, AuxJobKind.SUGGESTION) { awaitCancellation() }
            assertFalse(title.isCancelled)
            assertFalse(suggestion.isCancelled)
            registry.cancelAll(id)
            assertTrue(title.isCancelled)
            assertTrue(suggestion.isCancelled)
        } finally { scope.cancel() }
    }

    @Test
    fun `stopping one conversation keeps the other running`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = AuxJobRegistry()
        val first = Uuid.random()
        val second = Uuid.random()
        try {
            val firstJob = registry.launch(scope, first, AuxJobKind.TITLE) { awaitCancellation() }
            val secondJob = registry.launch(scope, second, AuxJobKind.TITLE) { awaitCancellation() }
            registry.cancelAll(first)
            assertTrue(firstJob.isCancelled)
            assertFalse(secondJob.isCancelled)
        } finally { scope.cancel() }
    }

    @Test
    fun `completed jobs deregister without cancelling their replacements`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = AuxJobRegistry()
        val id = Uuid.random()
        try {
            val finish = CompletableDeferred<Unit>()
            val job = registry.launch(scope, id, AuxJobKind.TITLE) { finish.await() }
            assertTrue(registry.isTracked(id, AuxJobKind.TITLE))
            finish.complete(Unit)
            job.join()
            assertFalse(registry.isTracked(id, AuxJobKind.TITLE))
            val newJob = registry.launch(scope, id, AuxJobKind.TITLE) { awaitCancellation() }
            assertTrue(registry.isTracked(id, AuxJobKind.TITLE))
            assertFalse(newJob.isCancelled)
        } finally { scope.cancel() }
    }

    @Test
    fun `cancelled jobs eventually deregister`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = AuxJobRegistry()
        val id = Uuid.random()
        try {
            val job = registry.launch(scope, id, AuxJobKind.SUGGESTION) { awaitCancellation() }
            registry.cancelAll(id)
            job.join()
            assertFalse(registry.isTracked(id, AuxJobKind.SUGGESTION))
        } finally { scope.cancel() }
    }
}
