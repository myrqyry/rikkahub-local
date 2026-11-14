package me.rerere.tts.mediapipe

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaPipePcmTest {
    @Test fun clampsToSignedLittleEndianPcm16() {
        val pcm = floatToPcm16(floatArrayOf(-3f,-1f,-0.5f,0f,0.5f,1f,5f))
        assertEquals(14, pcm.size)
        val b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(-32767,-32767,-16383,0,16383,32767,32767), List(7){b.short.toInt()})
    }
    @Test fun invalidSamplesBecomeSilence() {
        assertArrayEquals(ByteArray(6), floatToPcm16(floatArrayOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)))
    }
}
