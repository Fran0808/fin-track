package com.financemanager.listener

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import com.financemanager.listener.parser.YapeNotificationParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Execute regex guards on Android's ICU implementation, not only the desktop JVM. */
@RunWith(AndroidJUnit4::class)
class ParserPlatformCompatibilityTest {
    private val parser = YapeNotificationParser()

    @Test fun accentedIncomeAndNegatedOperationsUsePortableUnicodeBoundaries() {
        assertTrue(parser.parseResult(NotificationContent(text = "Persona Demo te envió S/ 12.30")) is ParseResult.Recognized)
        listOf("Persona Demo no te envió S/ 12.30", "No pagaste S/ 12.30 a Persona Demo",
            "Nunca te yapeó S/ 12.30").forEach { text ->
            assertTrue(parser.parseResult(NotificationContent(text = text)) is ParseResult.NonFinancial)
        }
    }

    @Test fun conflictingDirectionsCannotBecomeAnAutomaticPayment() {
        assertTrue(parser.parseResult(NotificationContent(text =
            "Pagaste S/ 12.30 a Persona Demo y Persona Demo te envió S/ 12.30")) is ParseResult.Ambiguous)
        // A trailing accented letter is still part of the word, not a boundary.
        assertFalse(parser.parseResult(NotificationContent(text =
            "Pagaste S/ 12.30 a Persona Demo y Persona Demo te envióó")) is ParseResult.Ambiguous)
    }
}
