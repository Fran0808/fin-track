package com.financemanager.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ManualEntryTest {
    @Test
    fun parsesValidDecimalAmountsAndRejectsInvalidOnes() {
        assertEquals(BigDecimal("15.50"), parseManualAmount("15.5"))
        assertEquals(BigDecimal("20.00"), parseManualAmount("20"))
        assertEquals(BigDecimal("8.75"), parseManualAmount("8,75"))
        assertNull(parseManualAmount(""))
        assertNull(parseManualAmount("0"))
        assertNull(parseManualAmount("-5"))
        assertNull(parseManualAmount("1.234"))
        assertNull(parseManualAmount("100000000"))
        assertNull(parseManualAmount("12,3.4"))
    }

    @Test
    fun buildsManualTransactionEntityCompatibleWithBackendContract() {
        val dt = LocalDateTime.of(2026, 11, 27, 10, 15, 30, 500_000_000)
        val entity = buildManualTransactionEntity(
            amount = BigDecimal("18.50"),
            flowType = "EXPENSE",
            contactName = "  Almuerzo ejecutivo  ",
            paymentMethod = MobilePaymentMethod.CASH,
            ownerKey = "owner-1",
            dateTime = dt,
            requestId = "123e4567-e89b-12d3-a456-426614174000"
        )
        assertEquals("18.50", entity.amount)
        assertEquals("EXPENSE", entity.flowType)
        assertEquals("Almuerzo ejecutivo", entity.contactName)
        assertEquals("CASH", entity.channel)
        assertEquals("2026-11-27T10:15:30", entity.transactionDate)
        assertEquals("manual:123e4567-e89b-12d3-a456-426614174000", entity.transactionHash)
        assertEquals("owner-1", entity.ownerKey)
        assertFalse(entity.isSynced)
    }
}
