package me.rerere.rikkahub.data.ai.compressor

import java.io.File

class CompressedContentStore(private val dir: File) {
    private val lock = Any()

    init { dir.mkdirs() }

    fun store(key: String, content: String) {
        synchronized(lock) {
            dir.resolve("$key.txt").writeText(content)
        }
    }

    fun retrieve(key: String): String? {
        return synchronized(lock) {
            val file = dir.resolve("$key.txt")
            if (file.exists()) file.readText() else null
        }
    }

    fun cleanup(maxEntries: Int = 200) {
        synchronized(lock) {
            val files = dir.listFiles()?.toList()?.sortedBy { it.lastModified() } ?: return
            if (files.size <= maxEntries) return
            files.take(files.size - maxEntries).forEach { it.delete() }
        }
    }
}
