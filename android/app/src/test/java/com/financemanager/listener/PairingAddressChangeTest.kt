package com.financemanager.listener

import com.financemanager.listener.data.PairingAddressChange
import com.financemanager.listener.data.PairingSession
import org.junit.Assert.*
import org.junit.Test

class PairingAddressChangeTest {
    private val original = PairingSession("test-device-token", "http://192.0.2.10:8080/", "existing-owner", 100)

    @Test fun verifiedAddressChangePreservesOwnerCredentialAndCaptureBoundary() {
        val changed = PairingAddressChange.verifiedSession(original, original, "http://192.0.2.20:8080", 1, 1)
        assertEquals("http://192.0.2.20:8080/", changed.serverUrl)
        assertEquals(original.ownerKey, changed.ownerKey)
        assertEquals(original.token, changed.token)
        assertEquals(original.startedAt, changed.startedAt)
    }

    @Test fun legacyCredentialScopeCanBeRetainedAfterVerification() {
        val changed = PairingAddressChange.verifiedSession(original, original, "https://example.test", null, 1)
        assertEquals(original.ownerKey, changed.ownerKey)
    }

    @Test fun aDifferentVerifiedUserCannotInheritPendingMovements() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingAddressChange.verifiedSession(original, original, "https://example.test", 1, 2)
        }
    }

    @Test fun changedOrClearedPairingCannotBeUpdatedByAnOldResponse() {
        for (current in listOf(null, original.copy(token = "another-token"), original.copy(ownerKey = "another-owner"))) {
            assertThrows(IllegalStateException::class.java) {
                PairingAddressChange.verifiedSession(current, original, "https://example.test", 1, 1)
            }
        }
    }

    @Test fun missingUserAndUnsafeAddressesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingAddressChange.verifiedSession(original, original, "https://example.test", 1, 0)
        }
        for (address in listOf("192.0.2.20:8080", "file:///test", "https://user:secret@example.test", "https://example.test?token=test")) {
            assertThrows(Exception::class.java) {
                PairingAddressChange.verifiedSession(original, original, address, 1, 1)
            }
        }
    }
}
