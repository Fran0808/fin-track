package com.financemanager.listener

import com.financemanager.listener.data.LocalTransactionEntity
import com.financemanager.listener.worker.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException

class ManualSyncDispatcherTest {
    @Test fun manualSendStartsDirectlyAndCanRetryAfterNetworkFailure() = runBlocking {
        val dispatcher = ManualSyncDispatcher(CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        val entry = LocalTransactionEntity(amount = "50.00", flowType = "INCOME", contactName = "Test Sender",
            transactionDate = "2026-10-16T12:00:00", transactionHash = "same-hash", rawText = "test", ownerKey = "owner")
        var pending = true
        val sent = mutableListOf<String>()
        var outcome: SyncOutcome? = null
        var problem: SyncProblem? = null
        assertTrue(dispatcher.start {
            outcome = SyncCoordinator.serialized {
                SyncEngine.run(listOf(entry), "owner", { true },
                    { sent.add(it.transactionHash); throw ConnectException("private detail") },
                    { pending = false }, { problem = it })
            }
        })
        assertEquals(SyncOutcome.RETRY, outcome)
        assertEquals(SyncFailure.CONNECTION, problem?.failure)
        assertTrue(pending)
        assertTrue(dispatcher.start {
            outcome = SyncCoordinator.serialized {
                SyncEngine.run(listOf(entry), "owner", { true },
                    { sent.add(it.transactionHash); SyncReply(201, listOf(it.transactionHash)) },
                    { pending = false })
            }
        })
        assertEquals(SyncOutcome.COMPLETE, outcome)
        assertFalse(pending)
        assertEquals(listOf("same-hash", "same-hash"), sent)
    }

    @Test fun repeatedTapsDoNotAccumulateAttempts() = runBlocking {
        val dispatcher = ManualSyncDispatcher(CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        assertTrue(dispatcher.start { attempts++; release.await() })
        repeat(5) { assertFalse(dispatcher.start { attempts++ }) }
        assertEquals(1, attempts)
        release.complete(Unit)
        assertTrue(dispatcher.start { attempts++ })
        assertEquals(2, attempts)
    }

    @Test fun manualAttemptWaitsForActiveUploadAndReadsPendingRowsAfterIt() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined)
        val dispatcher = ManualSyncDispatcher(scope)
        val release = CompletableDeferred<Unit>()
        var pending = true
        var sends = 0
        var manualFinished = false
        val automatic = scope.launch {
            SyncCoordinator.serialized { release.await(); sends++; pending = false }
        }
        assertTrue(dispatcher.start {
            SyncCoordinator.serialized {
                if (pending) sends++
                manualFinished = true
            }
        })
        assertFalse(manualFinished)
        release.complete(Unit)
        automatic.join()
        assertTrue(manualFinished)
        assertEquals(1, sends)
    }

    @Test fun unpairingCancelsManualAttemptWaitingForActiveUpload() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined)
        val dispatcher = ManualSyncDispatcher(scope)
        val release = CompletableDeferred<Unit>()
        val automatic = scope.launch { SyncCoordinator.serialized { release.await() } }
        var sent = false
        assertTrue(dispatcher.start { SyncCoordinator.serialized { sent = true } })
        dispatcher.cancel()
        release.complete(Unit)
        automatic.join()
        assertFalse(sent)
        assertTrue(dispatcher.start { SyncCoordinator.serialized { sent = true } })
        assertTrue(sent)
    }
}
