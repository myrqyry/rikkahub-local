package me.rerere.tts.matcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

class MatchaTtsBundle private constructor(
    val directory: File,
    val config: JsonObject,
    val g2pMeta: JsonObject,
) : AutoCloseable {
    val textEncoderFile = File(directory, TEXT_ENCODER)
    val decoderFile = File(directory, DECODER)
    val vocoderFile = File(directory, VOCODER)
    val g2pFile = File(directory, G2P)
    val embeddingFile = File(directory, EMBEDDING)
    val dictionaryFile = File(directory, DICTIONARY)

    override fun close() = Unit

    companion object {
        const val TEXT_ENCODER = "matcha_textenc_fp16.tflite"
        const val DECODER = "matcha_decoder_fp16.tflite"
        const val VOCODER = "matcha_vocoder_fp16.tflite"
        const val G2P = "dp_g2p_matcha_fp16.tflite"
        const val EMBEDDING = "emb.bin"
        const val DICTIONARY = "g2p_dict.txt.gz"
        const val CONFIG = "config.json"
        const val G2P_META = "g2p_meta.json"

        val requiredFiles = listOf(
            TEXT_ENCODER,
            DECODER,
            VOCODER,
            G2P,
            EMBEDDING,
            DICTIONARY,
            CONFIG,
            G2P_META,
        )

        private val json = Json { ignoreUnknownKeys = true }

        fun open(directory: File): MatchaTtsBundle {
            require(directory.isDirectory) {
                "Matcha TTS model directory does not exist: ${directory.absolutePath}"
            }
            val missing = requiredFiles.filterNot { File(directory, it).isFile }
            require(missing.isEmpty()) {
                "Matcha TTS bundle missing: ${missing.joinToString()}"
            }
            val config = parseObject(File(directory, CONFIG))
            val g2pMeta = parseObject(File(directory, G2P_META))
            return MatchaTtsBundle(directory, config, g2pMeta)
        }

        private fun parseObject(file: File): JsonObject = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Matcha TTS metadata: ${file.name}", e)
        }
    }
}
