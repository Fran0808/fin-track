package com.financemanager.listener

import com.financemanager.listener.data.LocalTransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MovementFiltersTest {
    private fun row(day: String, owner: String? = "owner", synced: Boolean = false) = LocalTransactionEntity(
        amount = "10.00", flowType = "INCOME", contactName = "Example Sender", transactionDate = "${day}T23:59:59",
        transactionHash = "test-$day", rawText = "synthetic", ownerKey = owner, isSynced = synced
    )
    @Test fun dateRangeIncludesBothEndsAndSingleDay() {
        val rows = listOf(row("2026-10-01"), row("2026-10-02"), row("2026-10-03"))
        assertEquals(2, filterMovements(rows, "", MovementStatus.ALL, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02")).size)
        assertEquals(listOf(rows[1]), filterMovements(rows, "", MovementStatus.ALL, LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-02")))
    }
    @Test fun pendingDoesNotIncludeUnassignedAndSearchComposesWithStatus() {
        val pending = row("2026-10-01")
        val unassigned = row("2026-10-02", owner = null)
        val sent = row("2026-10-03", synced = true)
        val rows = listOf(pending, unassigned, sent)
        assertEquals(listOf(pending), filterMovements(rows, " example ", MovementStatus.TO_SEND))
        assertEquals(listOf(unassigned), filterMovements(rows, "", MovementStatus.UNASSIGNED))
        assertEquals(listOf(sent), filterMovements(rows, "10.00", MovementStatus.SENT))
        assertTrue(filterMovements(rows, "missing", MovementStatus.ALL).isEmpty())
    }
    @Test fun emptyReviewStillAllowsShowingAccountRows() {
        val rows = listOf(row("2026-10-01"))
        assertTrue(filterMovements(rows, "", MovementStatus.UNASSIGNED).isEmpty())
        assertEquals(rows, filterMovements(rows, "", MovementStatus.ALL))
    }

    @Test fun oldSentRecordsRemainVisibleWithoutRequestingDestinationAgain() {
        val oldSent = row("2026-10-01", owner = null, synced = true)
        val pendingDestination = row("2026-10-02", owner = null)
        val current = row("2026-10-03")
        val rows = listOf(oldSent, pendingDestination, current)
        assertEquals(rows, filterMovements(rows, "", MovementStatus.ALL))
        assertEquals(listOf(oldSent), filterMovements(rows, "", MovementStatus.SENT))
        assertEquals(listOf(pendingDestination), filterMovements(rows, "", MovementStatus.UNASSIGNED))
        assertEquals(listOf(current), filterMovements(rows, "", MovementStatus.TO_SEND))
    }
}
