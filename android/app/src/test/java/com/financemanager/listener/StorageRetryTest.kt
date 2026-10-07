package com.financemanager.listener

import com.financemanager.listener.data.StorageRetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StorageRetryTest {
    @Test fun transientFailureIsRetriedWithoutChangingTheCapturedValue() = runBlocking {
        var attempts = 0
        val saved = StorageRetry.run({ it is IOException }, {}) {
            attempts++
            if (attempts < 3) throw IOException()
            "original-event-id"
        }
        assertEquals(3, attempts)
        assertEquals("original-event-id", saved)
    }

    @Test fun permanentFailureIsNotRetriedAndLaterWorkCanProceed() = runBlocking {
        var attempts = 0
        try {
            StorageRetry.run({ it is IOException }, {}) { attempts++; throw IllegalStateException() }
            fail("Expected storage failure")
        } catch (_: IllegalStateException) { }
        assertEquals(1, attempts)
        assertEquals("next-event", StorageRetry.run({ false }, {}) { "next-event" })
    }

    @Test fun transientRetriesAreBounded() = runBlocking {
        var attempts = 0
        try {
            StorageRetry.run({ true }, {}) { attempts++; throw IOException() }
            fail("Expected storage failure")
        } catch (_: IOException) { }
        assertEquals(3, attempts)
    }

    @Test fun cancellationNeverRetries() = runBlocking {
        var attempts = 0
        try {
            StorageRetry.run({ true }, {}) { attempts++; throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(1, attempts)
    }
}
