package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeTurnGateTest {
    @Test fun successfulTurnIsTerminalAndCannotBeCancelledAfterward() {
        val gate = NativeTurnGate()
        assertTrue(gate.acceptsOutput)
        assertEquals(NativeTurnGate.Terminal.Active, gate.nativeTerminal())
        assertFalse(gate.requestCancellation())
        assertFalse(gate.acceptsOutput)
        assertFalse(gate.wasCancelled)
        assertEquals(NativeTurnGate.Terminal.Duplicate, gate.nativeTerminal())
    }

    @Test fun cancellationDoesNotTurnLateOnDoneIntoSuccessfulWarmCache() {
        val gate = NativeTurnGate()
        assertTrue(gate.requestCancellation())
        assertFalse(gate.requestCancellation())
        assertFalse(gate.acceptsOutput)
        assertEquals(NativeTurnGate.Terminal.Cancelled, gate.nativeTerminal())
        assertTrue(gate.wasCancelled)
        assertEquals(NativeTurnGate.Terminal.Duplicate, gate.nativeTerminal())
    }

    @Test fun lateNativeErrorAfterCancelledFlowOnlyCompletesOnce() {
        val gate = NativeTurnGate()
        assertTrue(gate.requestCancellation())
        assertEquals(NativeTurnGate.Terminal.Cancelled, gate.nativeTerminal())
        assertEquals(NativeTurnGate.Terminal.Duplicate, gate.nativeTerminal())
    }

    @Test fun concurrentStopAndDoneCannotBothWinAsSuccessful() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(1000) {
                val gate = NativeTurnGate()
                val start = CountDownLatch(1)
                val done = pool.submit<NativeTurnGate.Terminal> {
                    start.await()
                    gate.nativeTerminal()
                }
                val stopped = pool.submit<Boolean> {
                    start.await()
                    gate.requestCancellation()
                }
                start.countDown()
                val terminal = done.get(3, TimeUnit.SECONDS)
                val cancelled = stopped.get(3, TimeUnit.SECONDS)
                assertEquals(cancelled, terminal == NativeTurnGate.Terminal.Cancelled)
                assertEquals(cancelled, gate.wasCancelled)
                assertEquals(NativeTurnGate.Terminal.Duplicate, gate.nativeTerminal())
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
