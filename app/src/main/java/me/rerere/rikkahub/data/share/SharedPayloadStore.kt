package me.rerere.rikkahub.data.share

data class SharedPayloadHandoff(
    val id: String,
    val payload: InboundSharePayload,
    val createdAt: Long,
)

interface SharedPayloadStore {
    fun put(handoff: SharedPayloadHandoff): String
    fun get(id: String): SharedPayloadHandoff?
    fun remove(id: String)
}

class InMemorySharedPayloadStore : SharedPayloadStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, SharedPayloadHandoff>()
    override fun put(handoff: SharedPayloadHandoff): String {
        map[handoff.id] = handoff
        return handoff.id
    }

    override fun get(id: String): SharedPayloadHandoff? = map[id]

    override fun remove(id: String) {
        map.remove(id)
    }
}
