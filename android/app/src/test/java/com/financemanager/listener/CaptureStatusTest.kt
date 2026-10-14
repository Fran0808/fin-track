package com.financemanager.listener

import com.financemanager.listener.service.*
import org.junit.Assert.*
import org.junit.Test

class CaptureStatusTest {
    @Test fun receivingAndRejectingDoesNotClaimSuccessfulSave() {
        val received = recordCapture(CaptureState(lastSaved = 5), CaptureEvent.RECEIVED, 10)
        val rejected = recordCapture(received, CaptureEvent.UNRECOGNIZED, 11)
        assertEquals(10L, rejected.lastNotification)
        assertEquals(5L, rejected.lastSaved)
        assertEquals(CaptureEvent.UNRECOGNIZED, rejected.history.first().event)
    }
    @Test fun successfulStorageClearsPreviousProcessingError() {
        val saved = recordCapture(CaptureState(lastError = "Previous error"), CaptureEvent.SAVED, 20)
        assertNull(saved.lastError)
        assertEquals(20L, saved.lastSaved)
        val duplicate = recordCapture(CaptureState(lastError = "Previous error"), CaptureEvent.DUPLICATE, 21)
        assertNull(duplicate.lastError)
        assertNull(duplicate.lastSaved)
    }
    @Test fun historyIsBoundedAndNewestFirst() {
        var state = CaptureState()
        repeat(40) { state = recordCapture(state, CaptureEvent.RECEIVED, it.toLong()) }
        assertEquals(30, state.history.size)
        assertEquals(39L, state.history.first().time)
        assertEquals(10L, state.history.last().time)
    }
}
