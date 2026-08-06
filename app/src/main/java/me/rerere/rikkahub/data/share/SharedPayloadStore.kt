package me.rerere.rikkahub.data.share

data class SharedPayloadHandoff(
    val id: String,
    val payload: InboundSharePayload,
    val createdAt: Long,
)

interface SharedPayloadStore {
    suspend fun put(handoff: SharedPayloadHandoff): String
    suspend fun get(id: String): SharedPayloadHandoff?
    suspend fun remove(id: String)
}

class InMemorySharedPayloadStore : SharedPayloadStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, SharedPayloadHandoff>()
    override suspend fun put(handoff: SharedPayloadHandoff): String {
        map[handoff.id] = handoff
        return handoff.id
    }

    override suspend fun get(id: String): SharedPayloadHandoff? = map[id]

    override suspend fun remove(id: String) {
        map.remove(id)
    }
}
