package me.rerere.rikkahub.skills.imports

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class ArtifactProvenanceStore(
    private val directory: File,
    private val json: Json,
) {
    init { directory.mkdirs() }

    @Synchronized
    fun save(kind: ArtifactKind, name: String, provenance: ArtifactProvenance) {
        directory.mkdirs()
        val target = fileFor(kind, name)
        val temporary = File(directory, ".${target.name}.tmp-${System.nanoTime()}")
        try {
            temporary.writeText(json.encodeToString(provenance))
            if (!temporary.renameTo(target)) {
                error("could not atomically persist artifact provenance")
            }
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun get(kind: ArtifactKind, name: String): ArtifactProvenance? =
        fileFor(kind, name).takeIf { it.isFile }?.let { file ->
            runCatching { json.decodeFromString<ArtifactProvenance>(file.readText()) }.getOrNull()
        }

    @Synchronized
    fun list(): List<Pair<ArtifactKind, ArtifactProvenance>> =
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                val kind = file.name.substringBefore('-').let { value ->
                    runCatching { ArtifactKind.valueOf(value) }.getOrNull()
                } ?: return@mapNotNull null
                runCatching { kind to json.decodeFromString<ArtifactProvenance>(file.readText()) }
                    .getOrNull()
            }

    private fun fileFor(kind: ArtifactKind, name: String): File =
        File(directory, "${kind.name}-${sha256(name)}.json")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
