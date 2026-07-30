package me.rerere.rikkahub.data.ai.memory

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AgentMemoryManager(context: Context) {
    private val file = File(context.filesDir, "agent_memory.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<MemEntry>> = _entries.asStateFlow()

    private fun load(): List<MemEntry> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<MemEntry>>(file.readText())
        } catch (_: Exception) { emptyList() }
    }

    private fun save(entries: List<MemEntry>) {
        file.writeText(json.encodeToString(entries))
    }

    fun store(type: String, content: String, tags: List<String> = emptyList()): MemEntry {
        val entry = MemEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            content = content,
            tags = tags,
        )
        val updated = _entries.value + entry
        _entries.value = updated
        save(updated)
        return entry
    }

    fun search(query: String, limit: Int = 10): List<MemEntry> {
        val q = query.lowercase()
        return _entries.value
            .filter { it.content.lowercase().contains(q) || it.type.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) } }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    fun recent(limit: Int = 20): List<MemEntry> =
        _entries.value.sortedByDescending { it.createdAt }.take(limit)
}
