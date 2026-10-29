package com.store.api.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualPaymentLabelsTest {
    @ParameterizedTest
    @CsvSource({"CASH,Efectivo", "CARD,Tarjeta", "BANK_TRANSFER,Transferencia", "WALLET,Billetera", "OTHER,Otro"})
    void analyticsUsesTheSameGenericPaymentMeaningAsManualEntry(String method, String label) {
        assertEquals(label, ChannelType.formatDisplayName(method,null));
    }
}
