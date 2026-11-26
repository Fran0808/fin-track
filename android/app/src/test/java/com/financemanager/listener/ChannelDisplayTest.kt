package com.financemanager.listener

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelDisplayTest {
    @Test
    fun formatsKnownChannelsToHumanReadableSpanishLabels() {
        assertEquals("Yape", formatChannelLabel("YAPE"))
        assertEquals("Plin", formatChannelLabel("PLIN"))
        assertEquals("Tarjeta de crédito", formatChannelLabel("TARJETA_CREDITO_BCP"))
        assertEquals("Tarjeta de débito", formatChannelLabel("TARJETA_DEBITO_SCOTIABANK"))
        assertEquals("Transferencia", formatChannelLabel("BBVA_TRANSFERENCIA"))
    }

    @Test
    fun formatsUnknownAndBlankChannelsSafely() {
        assertEquals("Billetera Movil", formatChannelLabel("BILLETERA_MOVIL"))
        assertEquals("Movimiento", formatChannelLabel("   "))
    }
}
