package com.financemanager.listener

import com.financemanager.listener.data.LocalTransactionEntity
import com.financemanager.listener.worker.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SyncEngineTest {
    private fun entry(hash: String, owner: String? = "a") = LocalTransactionEntity(
        amount = "50.00", flowType = "INCOME", contactName = "Test Sender",
        transactionDate = "2026-10-07T12:00:00", transactionHash = hash, rawText = "test", ownerKey = owner
    )

    @Test fun onlyCurrentOwnerIsSentAndAcknowledged() = runBlocking {
        val sent = mutableListOf<String>()
        val acknowledged = mutableListOf<String>()
        val result = SyncEngine.run(listOf(entry("one"), entry("other", "b"), entry("legacy", null)), "a", { true },
            { sent.add(it.transactionHash); SyncReply(201, listOf(it.transactionHash)) },
            { acknowledged.add(it.transactionHash) })
        assertEquals(SyncOutcome.COMPLETE, result)
        assertEquals(listOf("one"), sent)
        assertEquals(sent, acknowledged)
    }

    @Test fun changingAccountStopsRemainingRequests() = runBlocking {
        var current = true
        val sent = mutableListOf<String>()
        val result = SyncEngine.run(listOf(entry("one"), entry("two")), "a", { current },
            { sent.add(it.transactionHash); current = false; SyncReply(201, listOf(it.transactionHash)) }, {})
        assertEquals(SyncOutcome.SESSION_CHANGED, result)
        assertEquals(listOf("one"), sent)
    }

    @Test fun invalidRecordDoesNotBlockTheNextRecord() = runBlocking {
        val acknowledged = mutableListOf<String>()
        val result = SyncEngine.run(listOf(entry("bad"), entry("good")), "a", { true },
            { if (it.transactionHash == "bad") SyncReply(400, emptyList()) else SyncReply(201, listOf("good")) },
            { acknowledged.add(it.transactionHash) })
        assertEquals(SyncOutcome.REJECTED, result)
        assertEquals(listOf("good"), acknowledged)
    }

    @Test fun missingAcknowledgementDoesNotMarkAsSynced() = runBlocking {
        val result = SyncEngine.run(listOf(entry("one")), "a", { true },
            { SyncReply(201, listOf("wrong")) }, { fail("Unconfirmed entry was acknowledged") })
        assertEquals(SyncOutcome.RETRY, result)
    }

    @Test fun offlineFailurePreservesPendingEntry() = runBlocking {
        val result = SyncEngine.run(listOf(entry("one")), "a", { true },
            { throw IOException("offline") }, { fail("Offline entry was acknowledged") })
        assertEquals(SyncOutcome.RETRY, result)
    }

    @Test fun invalidCredentialsRequirePairingRatherThanNetworkRetry() = runBlocking {
        val result = SyncEngine.run(listOf(entry("one")), "a", { true },
            { SyncReply(401, emptyList()) }, { fail("Rejected entry was acknowledged") })
        assertEquals(SyncOutcome.AUTH_REQUIRED, result)
    }

    @Test fun cancellationIsPropagated() = runBlocking {
        try {
            SyncEngine.run(listOf(entry("one")), "a", { true }, { throw CancellationException() }, {})
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
