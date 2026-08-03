package me.rerere.tts.kitten

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * Loads the Kitten TTS Nano ONNX bundle: single model + voices.npz.
 *
 * Model: KittenML/kitten-tts-nano-0.1
 * - kitten_tts_nano_v0_1.onnx — input_ids [1, seq_len] INT64, style [1,256] FLOAT, speed [1] FLOAT
 *   → waveform [num_samples] FLOAT, duration [...].
 * - voices.npz — 8 voices, each (1,256) FLOAT32: expr-voice-{2,3,4,5}-{m,f}.
 * - config.json — metadata (model_file, voices file, version).
 *
 * ORT session is kept open for the lifetime of the bundle; [voices] are parsed into RAM.
 */
class KittenTtsBundle private constructor(
    val environment: OrtEnvironment,
    private val session: OrtSession,
    val voicesPath: File,
    val modelPath: File,
    val voices: Map<String, FloatArray>,
    val availableVoices: List<String>,
) : AutoCloseable {

    data class GraphInfo(val inputs: Set<String>, val outputs: Set<String>)

    fun graphInfo(): GraphInfo =
        GraphInfo(session.inputInfo.keys, session.outputInfo.keys)

    fun session(): OrtSession = session

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        const val MODEL_FILE = "kitten_tts_nano_v0_1.onnx"
        const val VOICES_FILE = "voices.npz"
        const val CONFIG_FILE = "config.json"

        val requiredFiles: List<String> = listOf(MODEL_FILE, VOICES_FILE)

        /** Loads NPZ voices as Map<name, FloatArray(256)>. Minimal NPZ parser — no dependency on numpy. */
        fun parseVoicesNpz(file: File): Map<String, FloatArray> {
            return NpzParser.parse(file)
        }

        fun open(
            directory: File,
            environment: OrtEnvironment? = null,
        ): KittenTtsBundle {
            require(directory.isDirectory) { "Kitten TTS bundle directory does not exist: $directory" }
            val missing = requiredFiles.filterNot { File(directory, it).isFile }
            require(missing.isEmpty()) { "Kitten TTS bundle missing: ${missing.joinToString()}" }

            val modelFile = File(directory, MODEL_FILE)
            val voicesFile = File(directory, VOICES_FILE)

            val ortEnv = environment ?: OrtEnvironment.getEnvironment()
            val session = try {
                ortEnv.createSession(modelFile.path)
            } catch (e: Throwable) {
                throw e
            }

            val voices: Map<String, FloatArray> = try {
                parseVoicesNpz(voicesFile)
            } catch (e: Throwable) {
                runCatching { session.close() }
                throw IllegalStateException("Failed to parse voices.npz: ${e.message}", e)
            }

            return KittenTtsBundle(
                environment = ortEnv,
                session = session,
                voicesPath = voicesFile,
                modelPath = modelFile,
                voices = voices,
                availableVoices = voices.keys.sorted(),
            )
        }
    }
}

/**
 * Minimal NPZ (ZIP of NPY) parser: returns Map<arrayName, FloatArray> for float32 (1,256) arrays.
 *
 * NPZ format: ZIP containing .npy files, each NPY header is \x93NUMPY + version + header_len + dict header
 * with descr '<f4', fortran_order False, shape (1,256) or (256,).
 */
internal object NpzParser {
    fun parse(file: File): Map<String, FloatArray> {
        val result = mutableMapOf<String, FloatArray>()
        java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.removeSuffix(".npy")
                    val npyBytes = zip.readBytes()
                    val array = parseNpy(npyBytes)
                    if (array != null) result[name] = array
                }
                entry = zip.nextEntry
            }
        }
        require(result.isNotEmpty()) { "No voice arrays found in ${file.name}" }
        return result
    }

    private fun parseNpy(bytes: ByteArray): FloatArray? {
        if (bytes.size < 10) return null
        // Magic \x93NUMPY
        if (bytes[0] != 0x93.toByte() || bytes[1] != 'N'.code.toByte()) return null
        // version: bytes[6]=major, [7]=minor
        val major = bytes[6].toInt()
        val headerLen = when (major) {
            1 -> {
                ((bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8))
            }
            2, 3 -> {
                ((bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8) or
                        ((bytes[10].toInt() and 0xFF) shl 16) or ((bytes[11].toInt() and 0xFF) shl 24))
            }
            else -> return null
        }
        val headerOff = if (major == 1) 10 else 12
        if (bytes.size < headerOff + headerLen) return null
        val headerStr = String(bytes, headerOff, headerLen, Charsets.US_ASCII)
        // Very light shape/descr parsing — we know it's '<f4' float32
        if (!headerStr.contains("<f4") && !headerStr.contains("|f4") && !headerStr.contains("float32")) {
            // Could still be float32 with different notation; attempt anyway
        }
        val dataOffset = headerOff + headerLen
        val nFloats = (bytes.size - dataOffset) / 4
        if (nFloats <= 0) return null
        val floats = FloatArray(nFloats)
        val bb = java.nio.ByteBuffer.wrap(bytes, dataOffset, nFloats * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until nFloats) floats[i] = bb.float
        return floats
    }
}
