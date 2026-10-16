package me.rerere.locallm

import java.io.File
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Import user-selected model files without overwriting an existing model or
 * exposing an incomplete copy to model discovery. Format validation does not
 * imply working inference.
 */
object ModelFileImport {
    fun copyVerified(
        input: InputStream,
        target: File,
        extension: String,
        checkpoint: () -> Unit = {},
    ): File = input.use { source ->
        val directory = target.parentFile ?: error("A model directory is required")
        require(directory.isDirectory || directory.mkdirs()) { "Could not create model directory" }
        check(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "A model named " + target.name + " already exists. Rename or remove it deliberately before importing."
        }
        val staging = File.createTempFile(".model-import-", ".partial", directory)
        try {
            staging.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkpoint()
                    val read = source.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
            val header = ByteArray(16)
            val count = staging.inputStream().use { it.read(header) }
            require(count >= 4 && ModelInstall.isValidMagicForExtension(
                extension, header.copyOf(count)
            )) { "The imported file does not have a valid model header" }
            checkpoint() // Cancellation before publication must not register a file.
            moveWithoutReplacing(staging, target)
            return target
        } finally {
            staging.delete()
        }
    }

    /** This applies to GGUF classification moves as well as primary imports.
     * Missing destination must not mean permission to replace an arrival racing
     * from another session. Never delete [source] when target already exists.
     */
    fun moveWithoutReplacing(source: File, target: File): File {
        check(source.isFile && !Files.isSymbolicLink(source.toPath())) { "Expected a regular imported model" }
        check(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "An installed model named " + target.name + " already exists"
        }
        try {
            Files.move(source.toPath(), target.toPath())
        } catch (collision: FileAlreadyExistsException) {
            throw IllegalStateException("Another model with this name was installed during import", collision)
        }
        return target
    }
}
