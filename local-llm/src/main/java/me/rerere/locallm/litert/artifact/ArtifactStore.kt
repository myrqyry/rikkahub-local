package me.rerere.locallm.litert.artifact

import java.io.InputStream

interface ArtifactResolver {
    suspend fun resolve(ref: ArtifactRef): ByteArray?

    suspend fun open(ref: ArtifactRef): InputStream?
}

interface ArtifactSink {
    suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef
}
