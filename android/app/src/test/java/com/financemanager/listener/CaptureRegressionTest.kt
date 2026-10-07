package com.financemanager.listener

import com.financemanager.listener.data.LegacyReconciliation
import com.financemanager.listener.data.PairingScope
import com.financemanager.listener.parser.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class CaptureRegressionTest {
    private val parser = YapeNotificationParser()

    @Test fun confirmedReceiptsRemainDistinctAndPreserveMaskedNames() {
        for (amount in listOf("50", "70", "23")) {
            val result = requireNotNull(parser.parse("Confirmación de Pago", "Persona Pr* te envió un pago por S/ $amount. El cód. de seguridad es: 123"))
            assertEquals(BigDecimal(amount), result.amount)
            assertEquals(FlowType.INCOME, result.flowType)
            assertEquals("Persona Pr*", result.contactName)
        }
    }

    @Test fun promotionsAmbiguousAmountsAndZeroAreRejected() {
        listOf("Gana S/ 50 a tu cuenta participando", "S/ 50 a tu cuenta", "Persona te envió S/ 25,50",
            "Persona te envió S/ 25.555", "Persona te envió S/ 1,23.50", "Persona te envió S/ 0",
            "Persona te envió S/ 100000000.00", "Persona te envió S/ 50 y gana S/ 5",
            "No pagaste S/ 50 a Persona", "Persona te envió S/ 1 250.50",
            "Persona te envió y pagaste S/ 50 a Otra Persona").forEach {
            assertNull(it, parser.parse(null, it))
        }
    }

    @Test fun invalidExpandedTextCannotFallBackToTruncatedShortAmount() {
        assertNull(parser.parse("Confirmación de Pago", "Persona te envió S/ 25", "Persona te envió S/ 25,50"))
    }

    @Test fun authorizedPackageOnly() {
        val dispatcher = NotificationParserDispatcher()
        assertNull(dispatcher.parse("com.android.shell", "Yapeaste", "S/ 50 a Persona"))
        assertNull(dispatcher.parse("com.example.app", null, "Persona te envió S/ 50"))
    }

    @Test fun repeatedRecoveryUsesPublicationDateAndStableHash() {
        val receipt = requireNotNull(parser.parse(null, "Persona te envió S/ 50"))
        val posted = 1_750_000_000_000L
        val metadata = NotificationMetadata("key-one", posted)
        val first = NotificationIdentity.apply(receipt, metadata)
        val again = NotificationIdentity.apply(receipt.copy(transactionDate = receipt.transactionDate.plusDays(1)), metadata)
        assertEquals(first.transactionHash, again.transactionHash)
        assertEquals(Instant.ofEpochMilli(posted).atZone(ZoneId.systemDefault()).toLocalDateTime(), again.transactionDate)
    }

    @Test fun equalPaymentsWithinMinuteHaveDifferentAndroidIdentities() {
        val receipt = requireNotNull(parser.parse(null, "Persona te envió S/ 50"))
        val one = NotificationIdentity.apply(receipt, NotificationMetadata("one", 1000))
        val two = NotificationIdentity.apply(receipt, NotificationMetadata("two", 1001))
        val reused = NotificationIdentity.apply(receipt, NotificationMetadata("one", 1002))
        assertNotEquals(one.transactionHash, two.transactionHash)
        assertNotEquals(one.transactionHash, reused.transactionHash)
    }

    @Test fun presentationUpdatesKeepIdentityWhenEventTimestampIsPreserved() {
        val one = requireNotNull(parser.parse(null, "Persona te envió S/ 50.00"))
        val two = requireNotNull(parser.parse(null, "Persona te envió S/ 50.00 a tu Yape"))
        assertEquals(NotificationIdentity.apply(one, NotificationMetadata("one", 1000, 900)).transactionHash,
            NotificationIdentity.apply(two, NotificationMetadata("one", 2000, 900)).transactionHash)
    }

    @Test fun differentSecurityCodesIdentifyDifferentPayments() {
        val one = requireNotNull(parser.parse(null, "Persona te envió S/ 50. El cód. de seguridad es: 111"))
        val two = requireNotNull(parser.parse(null, "Persona te envió S/ 50. El cód. de seguridad es: 222"))
        assertNotEquals(NotificationIdentity.fingerprint(one), NotificationIdentity.fingerprint(two))
    }

    @Test fun ownershipIncludesServerAndVerifiedUser() {
        assertEquals(PairingScope.ownerKey("https://example.test", "old", 1), PairingScope.ownerKey("https://example.test:443/", "new", 1))
        assertNotEquals(PairingScope.ownerKey("https://example.test", "token", 1), PairingScope.ownerKey("https://example.test", "token", 2))
        assertNotEquals(PairingScope.ownerKey("https://example.test", "token", 1), PairingScope.ownerKey("https://other.test", "token", 1))
    }

    @Test fun legacyMatchingIsBoundedAndRejectsInvalidDates() {
        assertTrue(LegacyReconciliation.matches("2026-10-07T12:00:30", "2026-10-07T12:00:00"))
        assertFalse(LegacyReconciliation.matches("2026-10-07T12:05:00", "2026-10-07T12:00:00"))
        assertFalse(LegacyReconciliation.matches("invalid", "2026-10-07T12:00:00"))
    }
}
