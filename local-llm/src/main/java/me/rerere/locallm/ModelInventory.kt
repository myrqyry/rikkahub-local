package me.rerere.locallm

class ModelInventory {
    private val entries = mutableMapOf<String, ModelEntry>()

    fun add(entry: ModelEntry) { entries[entry.id] = entry }
    fun remove(id: String) { entries.remove(id) }
    fun getById(id: String): ModelEntry? = entries[id]
    fun list(): List<ModelEntry> = entries.values.toList()
    fun listByRuntime(runtime: LocalRuntime): List<ModelEntry> =
        entries.values.filter { it.runtime == runtime }
    fun findByFilePath(path: String): ModelEntry? =
        entries.values.firstOrNull { it.filePath == path }
}
