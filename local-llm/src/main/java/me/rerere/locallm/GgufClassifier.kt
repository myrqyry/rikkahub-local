package me.rerere.locallm

import java.io.ByteArrayInputStream
import java.io.File

/**
 * Classifies a GGUF file's role by inspecting its header metadata and tensor
 * names, rather than trusting the `.gguf` extension alone.
 *
 * llama.cpp-written GGUFs always carry `general.architecture` (e.g. "llama",
 * "qwen2"). The vendored stable-diffusion.cpp writer emits NO metadata keys,
 * so image GGUFs are detected by tensor-name prefix.
 */
object GgufClassifier {

    private const val MAGIC = 0x46554747 // "GGUF" little-endian
    private const val HEADER_SCAN_BYTES = 64 * 1024

    // GGUFValueType
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9

    private val IMAGE_ARCHITECTURES = setOf(
        "sd", "sdxl", "sd3", "svd", "flux", "magi", "mage", "pixart", "playground", "hunyuan", "ltx",
    )

    // Order matters: check specific "model.<sub>" prefixes before any bare "model."
    private val IMAGE_TENSOR_PREFIXES = listOf(
        "model.diffusion_model.",
        "model.language_model.",
        "cond_stage_model.",
        "first_stage_model.",
        "text_encoder.",
        "image_encoder.",
        "te_encoder.",
        "vae.",
        "unet.",
        "single_blocks.",
        "double_blocks.",
    )

    private val LLM_TENSOR_PREFIXES = listOf(
        "token_embd.",
        "output_norm.",
        "output.",
        "blk.",
        "tok_embeddings.",
        "model.embed_tokens.",
        "lm_head.",
    )

    /**
     * Convenience: classify a GGUF file on disk. Reads the first
     * [HEADER_SCAN_BYTES] (enough for header + metadata + first tensor name)
     * and delegates to [classify]. Returns null for missing/unreadable files.
     */
    fun classifyFile(file: File): LocalRuntime? {
        if (!file.isFile || !file.exists()) return null
        return try {
            val header = file.readBytes().let { if (it.size > HEADER_SCAN_BYTES) it.copyOf(HEADER_SCAN_BYTES) else it }
            classify(header)
        } catch (_: Exception) {
            null
        }
    }

    fun classify(firstBytes: ByteArray): LocalRuntime? {        if (firstBytes.size < 24) return null
        val reader = Reader(firstBytes)
        if (reader.readU32() != MAGIC) return null
        reader.readU32() // version
        reader.readU64() // tensor_count
        val metadataKvCount = reader.readU64()

        var architecture: String? = null
        repeat(metadataKvCount.toInt()) {
            if (reader.isExhausted) return null
            val key = reader.readString() ?: return null
            val type = reader.readU32()
            val value = reader.readValue(type) ?: return null
            if (key == "general.architecture" && type == TYPE_STRING) {
                architecture = value as? String
            }
        }

        architecture?.let { arch ->
            return if (arch.lowercase() in IMAGE_ARCHITECTURES) {
                LocalRuntime.StableDiffusion
            } else {
                LocalRuntime.LlamaCpp
            }
        }

        // No architecture key (sd.cpp writer emits none): fall back to the first
        // tensor name. Tensor-info: name (u64 len + bytes), n_dims u32, dims
        // u32[n], ggml_type u32, offset u64.
        val tensorName = reader.readString() ?: return null
        return when {
            IMAGE_TENSOR_PREFIXES.any { tensorName.startsWith(it) } -> LocalRuntime.StableDiffusion
            LLM_TENSOR_PREFIXES.any { tensorName.startsWith(it) } -> LocalRuntime.LlamaCpp
            else -> null
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0
        val isExhausted: Boolean get() = pos >= bytes.size

        private fun ensure(n: Int): Boolean = pos + n <= bytes.size

        fun readU32(): Int {
            if (!ensure(4)) return -1
            val v = (bytes[pos].toInt() and 0xFF) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun readU64(): Long {
            if (!ensure(8)) return -1
            var v = 0L
            for (i in 0 until 8) {
                v = v or ((bytes[pos + i].toLong() and 0xFF) shl (8 * i))
            }
            pos += 8
            return v
        }

        fun readString(): String? {
            val len = readU64()
            if (len < 0 || len > Int.MAX_VALUE || !ensure(len.toInt())) return null
            val s = String(bytes, pos, len.toInt(), Charsets.UTF_8)
            pos += len.toInt()
            return s
        }

        fun readValue(type: Int): Any? = when (type) {
            0 -> readU32().let { it and 0xFF }
            1 -> readU32().let { it.toByte() }
            2 -> readU32().let { it and 0xFFFF }
            3 -> readU32().let { it.toShort() }
            4, 5, 6 -> readU32()
            7 -> readU32().let { it != 0 }
            TYPE_STRING -> readString()
            TYPE_ARRAY -> {
                val elemType = readU32()
                val count = readU64()
                if (count < 0 || count > 1_000_000) return null
                repeat(count.toInt()) {
                    if (readValue(elemType) == null) return null
                }
                emptyList<Any?>()
            }
            10, 11 -> readU64()
            12 -> {
                if (!ensure(8)) return null
                pos += 8
                0.0
            }
            else -> null
        }
    }
}
