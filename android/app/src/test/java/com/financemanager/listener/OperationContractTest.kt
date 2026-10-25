package com.financemanager.listener

import com.financemanager.listener.network.SyncReceipt
import com.financemanager.listener.network.TransactionSyncDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OperationContractTest {
    private val gson = Gson()

    @Test fun oldAndNewReceiptsPreserveHashAcknowledgement() {
        val old = gson.fromJson("""{"transactionHash":"synthetic"}""", SyncReceipt::class.java)
        val current = gson.fromJson("""{"transactionHash":"synthetic","operationType":"OWN_CARD_PAYMENT"}""", SyncReceipt::class.java)
        val future = gson.fromJson("""{"transactionHash":"synthetic","operationType":"FUTURE_OPERATION"}""", SyncReceipt::class.java)
        assertEquals("synthetic", old.transactionHash)
        assertNull(old.operationType)
        assertEquals(old.transactionHash, current.transactionHash)
        assertEquals("OWN_CARD_PAYMENT", current.operationType)
        assertEquals(old.transactionHash, future.transactionHash)
    }

    @Test fun existingDeviceRequestsDoNotInventAnOperationType() {
        val request = TransactionSyncDto(BigDecimal("12.34"), "EXPENSE", "Synthetic contact",
            transactionDate = "2026-01-01T12:00:00", transactionHash = "synthetic")
        val json = gson.toJsonTree(request).asJsonObject
        assertFalse(json.has("operationType"))
        assertEquals(BigDecimal("12.34"), json.get("amount").asBigDecimal)
        assertEquals("EXPENSE", json.get("flowType").asString)
    }
}
