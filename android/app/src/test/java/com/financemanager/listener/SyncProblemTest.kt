package com.financemanager.listener

import com.financemanager.listener.worker.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class SyncProblemTest {
    @Test fun networkFailuresAreDistinguishedWithoutExposingExceptionContents() {
        listOf(
            ConnectException("private payload") to SyncFailure.CONNECTION,
            SocketTimeoutException("private payload") to SyncFailure.TIMEOUT,
            UnknownHostException("private payload") to SyncFailure.HOST,
            SSLException("private payload") to SyncFailure.SECURE_CONNECTION
        ).forEach { (exception, expected) ->
            val problem = syncProblem(exception)
            assertEquals(expected, problem.failure)
            assertFalse(problem.message.contains("private payload"))
        }
    }

    @Test fun httpDiagnosticIncludesStatusWithoutResponseContent() {
        assertTrue(SyncProblem(SyncFailure.HTTP, 503).message.contains("503"))
        assertEquals(SyncFailure.LOCAL, syncProblem(IllegalStateException("private payload")).failure)
    }
}
