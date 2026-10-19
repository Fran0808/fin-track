package com.financemanager.listener

import org.junit.Assert.*
import org.junit.Test
import com.financemanager.listener.capture.DurableCaptureQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class DurableCaptureQueueTest {
    @Test fun queuePreservesOrderAndReportsSaturation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val stored = Channel<Int>(Channel.UNLIMITED)
        try {
            val queue = DurableCaptureQueue<Int>(scope, 1, persist = { gate.await(); stored.send(it) }, onCommitted = {}, onFailure = { fail("Unexpected failure") })
            assertTrue(queue.offer(1))
            assertTrue(queue.offer(2))
            assertFalse(queue.offer(3))
            gate.complete(Unit)
            withTimeout(1000) { assertEquals(1, stored.receive()); assertEquals(2, stored.receive()) }
        } finally { scope.cancel() }
    }

    @Test fun failedStorageIsObservableAndDoesNotStopNextCapture() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failures = 0
        var committed = 0
        try {
            val queue = DurableCaptureQueue<Int>(scope, persist = { if (it == 1) error("Synthetic disk failure") },
                onCommitted = { committed++ }, onFailure = { failures++ })
            assertTrue(queue.offer(1)); assertTrue(queue.offer(2))
            assertEquals(1, failures)
            assertEquals(1, committed)
        } finally { scope.cancel() }
    }
}
