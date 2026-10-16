package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/** Title and suggestions are auxiliary model requests and must not accumulate behind
 * a main conversation on a limited local inference server. */
internal enum class AuxJobKind { TITLE, SUGGESTION }

/** Replace older work only for the same conversation and kind. A completed or
 * cancelled job removes only its own registration, even if a replacement exists. */
internal class AuxJobRegistry {
    private val jobs = ConcurrentHashMap<Pair<Uuid, AuxJobKind>, Job>()
    private val lock = Any()

    fun launch(
        scope: CoroutineScope,
        conversationId: Uuid,
        kind: AuxJobKind,
        block: suspend () -> Unit,
    ): Job {
        val key = conversationId to kind
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        synchronized(lock) {
            jobs.put(key, job)?.cancel()
            // Install the handler only after registration, so immediate completion
            // (e.g. a cancelled parent scope) cannot leave an orphaned map entry.
            job.invokeOnCompletion { jobs.remove(key, job) }
        }
        job.start()
        return job
    }

    fun cancelAll(conversationId: Uuid) {
        synchronized(lock) {
            jobs.forEach { (key, job) ->
                if (key.first == conversationId) job.cancel()
            }
        }
    }

    internal fun isTracked(conversationId: Uuid, kind: AuxJobKind): Boolean =
        jobs.containsKey(conversationId to kind)
}
