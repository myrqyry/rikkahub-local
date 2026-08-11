package me.rerere.asr.providers

import java.io.ByteArrayOutputStream

internal object WhisperByteLevelCodec {
    private val charToByte = buildMap {
        val direct = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) +
            ('®'.code..'ÿ'.code)).toHashSet()
        var next = 256
        for (byte in 0 until 256) {
            val mapped = if (byte in direct) byte else next++
            put(mapped.toChar(), byte.toByte())
        }
    }

    fun decodeToken(token: String): ByteArray {
        val bytes = ByteArrayOutputStream(token.length)
        for (char in token) {
            val byte = charToByte[char]
                ?: return token.toByteArray(Charsets.UTF_8)
            bytes.write(byte.toInt())
        }
        return bytes.toByteArray()
    }
}
