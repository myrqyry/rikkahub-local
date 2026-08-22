package me.rerere.locallm.llamacpp

import java.io.File

object MmprojLocator {
    fun findMmproj(modelFile: File): File? {
        if (!modelFile.isFile) return null
        val dir = modelFile.parentFile ?: return null
        val base = modelFile.name.substringBeforeLast('.')
        val candidates = listOf(
            File(dir, "$base.mmproj.gguf"),
            File(dir, "mmproj.gguf"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
