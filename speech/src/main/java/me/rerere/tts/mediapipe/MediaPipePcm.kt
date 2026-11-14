package me.rerere.tts.mediapipe

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** MediaPipe returns normalized 32-bit float audio; AudioTrack consumes 16-bit little-endian PCM. */
internal fun floatToPcm16(samples: FloatArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    val bytes = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in samples) {
        val safe = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        bytes.putShort((safe * 32767f).toInt().toShort())
    }
    return out
}
