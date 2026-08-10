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
        val chars = FloatArray(96)
        chars[0] = start.toFloat()
        word.take(94).forEachIndexed { index, char ->
            chars[index + 1] = (charToIndex[char.toString()] ?: 0).toFloat()
        }
        chars[minOf(word.length + 1, 95)] = end.toFloat()
        val inputs = model.createInputBuffers()
        val outputs = model.createOutputBuffers()
        inputs.first().writeFloat(chars)
        model.run(inputs, outputs)
        val logits = outputs.first().readFloat()
        val phonemes = buildString {
            val frames = minOf(96, logits.size / max(1, indexToPhoneme.size))
            repeat(frames) { frame ->
                val offset = frame * indexToPhoneme.size
                val id = (0 until indexToPhoneme.size).maxByOrNull { logits[offset + it] } ?: 0
                val phoneme = indexToPhoneme[id].orEmpty()
                if (phoneme != "_" && phoneme != "<en_us>" && phoneme != "<end>") {
                    repeat(repeats) { append(phoneme) }
                }
            }
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
