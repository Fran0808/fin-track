package com.financemanager.listener

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import com.financemanager.listener.parser.FlowType
import com.financemanager.listener.parser.ScotiabankNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ScotiabankNotificationParserTest {

    private val parser = ScotiabankNotificationParser()

    @Test
    fun parsesIncomingPlinWithSenderAsPlinIncome() {
        val result = parser.parse(
            title = "¡Te plinearon!",
            text = "Diego León te plineó S/ 55.00"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("Diego León", result?.contactName)
        assertEquals(BigDecimal("55.00"), result?.amount)
    }

    @Test
    fun parsesGeneralIncomingPlinWithoutSender() {
        val result = parser.parse(
            title = "Recibiste un Plin",
            text = "Recibiste un Plin por S/ 70.00 en tu cuenta Scotiabank"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("Plin Scotiabank", result?.contactName)
        assertEquals(BigDecimal("70.00"), result?.amount)
    }

    @Test
    fun ignoresCardConsumptionsAndOutgoingPaymentsToAvoidEmailDuplicates() {
        assertNull(parser.parse("Consumo con tarjeta", "Realizaste un consumo de S/ 92.00 en RESTAURANTE"))
        assertNull(parser.parse("Plin enviado", "Plineaste S/ 18.00 a Sofía Castro"))
        val cardResult = parser.parseResult(
            NotificationContent("Consumo con tarjeta", "Consumo S/ 92.00 en RESTAURANTE", null, emptyList())
        )
        assertTrue(cardResult is ParseResult.NonFinancial)
    }

    @Test
    fun rejectsNegatedAndMultipleAmountPlinNotifications() {
        val negated = parser.parseResult(
            NotificationContent("Aviso Plin", "No te plineó S/ 30.00", null, emptyList())
        )
        assertTrue(negated is ParseResult.NonFinancial)

        val multiple = parser.parseResult(
            NotificationContent("Plin", "Recibiste un Plin de S/ 30.00 y otro de S/ 40.00", null, emptyList())
        )
        assertTrue(multiple is ParseResult.Ambiguous)
    }
}
