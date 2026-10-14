package com.financemanager.listener

import com.financemanager.listener.worker.SyncCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncCoordinatorTest {
    @Test fun manualAndAutomaticAttemptsReadPendingRowsSerially() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val secondQueued = CompletableDeferred<Unit>()
        var pending = true
        var sent = 0
        val automatic = async {
            SyncCoordinator.serialized {
                started.complete(Unit)
                release.await()
                if (pending) { sent++; pending = false }
            }
        }
        started.await()
        val manual = async {
            secondQueued.complete(Unit)
            SyncCoordinator.serialized { if (pending) { sent++; pending = false } }
        }
        secondQueued.await()
        assertFalse(manual.isCompleted)
        release.complete(Unit)
        automatic.await()
        manual.await()
        assertEquals(1, sent)
        assertFalse(pending)
    }
}
