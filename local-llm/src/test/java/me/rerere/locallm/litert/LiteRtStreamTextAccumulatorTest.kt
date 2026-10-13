package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtStreamTextAccumulatorTest {
    @Test
    fun fragmentedTokensProduceCompleteCumulativeSnapshots() {
        val accumulator = LiteRtStreamTextAccumulator()
        assertEquals("Y", accumulator.append("Y"))
        assertEquals("Yellow", accumulator.append("ellow"))
        assertEquals("Yellow.", accumulator.append("."))
    }

    @Test
    fun separateTurnsDoNotInheritPriorOutput() {
        val firstTurn = LiteRtStreamTextAccumulator()
        assertEquals("Hello", firstTurn.append("Hello"))

        val nextTurn = LiteRtStreamTextAccumulator()
        assertEquals("World", nextTurn.append("World"))
    }

    @Test
    fun emptyChunkDoesNotErasePreviouslyEmittedText() {
        val accumulator = LiteRtStreamTextAccumulator()
        assertEquals("answer", accumulator.append("answer"))
        assertEquals("answer", accumulator.append(""))
    }
}
