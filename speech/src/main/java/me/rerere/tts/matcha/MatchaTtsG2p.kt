package me.rerere.tts.matcha

import com.google.ai.edge.litert.CompiledModel
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.math.max

class MatchaTtsG2p(
    private val bundle: MatchaTtsBundle,
    private val neuralModel: CompiledModel? = null,
) : AutoCloseable {
    private val dictionary = loadDictionary()
    private val charToIndex = bundle.g2pMeta["char2idx"]!!.jsonObject
        .mapValues { it.value.toString().toInt() }
    private val indexToPhoneme = bundle.g2pMeta["idx2ph"]!!.jsonObject
        .mapKeys { it.key.toInt() }
        .mapValues { it.value.toString().trim('"') }
    private val repeats = bundle.g2pMeta["char_repeats"]?.toString()?.toInt() ?: 1
    private val start = bundle.g2pMeta["start"]?.toString()?.toInt() ?: 1
    private val end = bundle.g2pMeta["end"]?.toString()?.toInt() ?: 2

    fun phonemize(text: String): String = text
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .joinToString(" ") { token ->
            val normalized = token.trim { it.isPunctuation() }
            if (normalized.isEmpty()) return@joinToString token
            val pronunciation = dictionary[normalized.lowercase()] ?: infer(normalized)
            token.takeWhile(Char::isPunctuation) + pronunciation +
                token.takeLastWhile(Char::isPunctuation)
        }

    private fun infer(word: String): String {
        val model = neuralModel ?: throw IllegalArgumentException(
            "No neural G2P model available for out-of-vocabulary word: $word"
        )
        val chars = encodeG2pCharacters(word, charToIndex, repeats, start, end)
        val inputs = model.createInputBuffers()
        val outputs = model.createOutputBuffers()
        val phonemes = try {
            inputs.first().writeFloat(chars)
            model.run(inputs, outputs)
            decodeG2pCtc(outputs.first().readFloat(), indexToPhoneme)
        } finally {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
        }
        return phonemes.ifEmpty { word }
    }

    private fun loadDictionary(): Map<String, String> = buildMap {
        GZIPInputStream(FileInputStream(bundle.dictionaryFile)).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) put(line.substring(0, tab).lowercase(), line.substring(tab + 1))
                }
            }
        }
    }

    override fun close() = Unit
}

private fun Char.isPunctuation(): Boolean = !isLetterOrDigit() && this != '\''
