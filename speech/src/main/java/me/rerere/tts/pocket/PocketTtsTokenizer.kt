package me.rerere.tts.pocket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

private const val kNegativeInfinity = -1.0e30f

private val whitespaceMarker = byteArrayOf(0xE2.toByte(), 0x96.toByte(), 0x81.toByte())

private fun startsWithMarker(bytes: ByteArray): Boolean {
    if (bytes.size < whitespaceMarker.size) return false
    for (i in whitespaceMarker.indices) {
        if (bytes[i] != whitespaceMarker[i]) return false
    }
    return true
}

private class TrieNode {
    val next: HashMap<Int, Int> = HashMap()
    var tokenId: Int = -1
    var score: Float = 0.0f
}

class PocketTtsTokenizer(vocabJson: String, scoresJson: String) {

    private val trie: MutableList<TrieNode>
    private val idToToken: List<String>
    private val byteTokenId = IntArray(256) { -1 }
    private val byteTokenScore = FloatArray(256) { kNegativeInfinity }
    private val json = Json

    init {
        val vocabulary = json.parseToJsonElement(vocabJson).jsonObject
        val scores = json.parseToJsonElement(scoresJson).jsonObject
        check(!vocabulary.isEmpty()) { "Pocket TTS tokenizer JSON is empty" }
        if (vocabulary.size != scores.size) {
            throw IllegalStateException("Pocket TTS vocabulary and score table sizes differ")
        }

        val tokenToId = HashMap<String, Int>(vocabulary.size)
        val tokenToScore = HashMap<String, Float>(scores.size)
        val tokens = MutableList(vocabulary.size) { "" }
        idToToken = tokens
        for ((token, value) in vocabulary.entries) {
            val id = value.jsonPrimitive.int
            require(id >= 0 && id < tokens.size) {
                "Pocket TTS vocabulary contains an out-of-range token ID"
            }
            tokenToId[token] = id
            tokens[id] = token
        }
        for ((token, value) in scores.entries) {
            tokenToScore[token] = value.jsonPrimitive.float
        }

        trie = mutableListOf(TrieNode())
        for ((token, id) in tokenToId) {
            var node = 0
            for (byteValue in token.toByteArray(Charsets.UTF_8)) {
                val byte = byteValue.toInt() and 0xFF
                val next = trie[node].next[byte]
                node = if (next == null) {
                    val newNode = trie.size
                    trie[node].next[byte] = newNode
                    trie.add(TrieNode())
                    newNode
                } else {
                    next
                }
            }
            val score = tokenToScore[token]
                ?: throw IllegalStateException("Pocket TTS token is missing its score: $token")
            trie[node].tokenId = id
            trie[node].score = score
        }

        for (byte in 0 until 256) {
            val name = "<0x%02X>".format(byte)
            val id = tokenToId[name]
            val score = tokenToScore[name]
            if (id != null && score != null) {
                byteTokenId[byte] = id
                byteTokenScore[byte] = score
            }
        }
    }

    fun encodeIds(text: String): List<Int> {
        val raw = text.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream(raw.size + 8)
        for (byteValue in raw) {
            if (byteValue.toInt() and 0xFF == 0x20) {
                output.write(whitespaceMarker)
            } else {
                output.write(byteValue.toInt())
            }
        }
        val bytes: ByteArray = if (startsWithMarker(output.toByteArray())) {
            output.toByteArray()
        } else {
            whitespaceMarker + output.toByteArray()
        }

        val length = bytes.size
        val best = FloatArray(length + 1) { kNegativeInfinity }
        val back = IntArray(length + 1) { -1 }
        val backId = IntArray(length + 1) { -1 }
        best[length] = 0.0f

        for (start in length - 1 downTo 0) {
            var node = 0
            var end = start
            while (end < length) {
                val byte = bytes[end].toInt() and 0xFF
                val next = trie[node].next[byte] ?: break
                node = next
                val candidate = trie[node]
                if (candidate.tokenId >= 0) {
                    val score = candidate.score + best[end + 1]
                    if (score > best[start]) {
                        best[start] = score
                        back[start] = end + 1
                        backId[start] = candidate.tokenId
                    }
                }
                end++
            }
            if (back[start] < 0) {
                val byte = bytes[start].toInt() and 0xFF
                val fallbackId = byteTokenId[byte]
                if (fallbackId >= 0) {
                    best[start] = byteTokenScore[byte] + best[start + 1]
                    backId[start] = fallbackId
                }
                back[start] = start + 1
            }
        }

        val ids = mutableListOf<Int>()
        var offset = 0
        while (offset < length) {
            val next = back[offset]
            val id = backId[offset]
            if (next <= offset || id < 0 || id >= idToToken.size) {
                throw IllegalStateException("Pocket TTS tokenizer could not reconstruct its best path")
            }
            ids.add(id)
            offset = next
        }
        return ids
    }
}
