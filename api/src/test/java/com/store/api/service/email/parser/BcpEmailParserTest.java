package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BcpEmailParserTest {

    private BcpEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new BcpEmailParser();
    }

    @Test
    void shouldIgnoreSurveyRewards() {
        String body = "Te invitamos a participar de una encuesta. Si completas la encuesta "
                + "participarás por una Gift Card por un valor de S/ 500 y otras por S/ 100.";
        assertNull(parser.parse("Ayúdanos a saber lo que necesitas", body,
                LocalDateTime.of(2026, 10, 1, 12, 39, 26)));
    }

    @Test
    void shouldNotInterpretAnAmountOrBankNameAsAnOperation() {
        assertNull(parser.parse("Beneficios BCP", "Monto: S/ 500.00. Participa por un premio.",
                LocalDateTime.now()));
        assertNull(parser.parse("Información sobre abonos", "Abono promocional de S/ 500.00",
                LocalDateTime.now()));
    }

    @Test
    void shouldUseTheOperationAmountInsteadOfAnUnrelatedReward() {
        ParsedEmailTransaction tx = parser.parse("Aviso de operación: Consumo con Tarjeta de Crédito",
                "Participa por una Gift Card de S/ 500. Realizaste un consumo de S/ 25.50. "
                        + "Monto: S/ 25.50 Establecimiento: TIENDA EJEMPLO Nro. de operación: 98765432",
                LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(new BigDecimal("25.50"), tx.getAmount());
    }

    @Test
    void shouldRejectConflictingOperationAmountsOrCurrencies() {
        String subject = "Constancia de pago BCP";
        assertNull(parser.parse(subject, "Monto: S/ 25.00 Importe: S/ 50.00", LocalDateTime.now()));
        assertNull(parser.parse(subject, "Monto: S/ 25.00 Importe: US$ 25.00", LocalDateTime.now()));
        assertNull(parser.parse(subject, "Realizaste una transferencia por S/ 25.00 Monto: S/ 50.00",
                LocalDateTime.now()));
    }

    @Test
    void shouldRequireAnOperationAmountWithCurrency() {
        String subject = "Constancia de pago BCP";
        assertNull(parser.parse(subject, "Monto: 500", LocalDateTime.now()));
        assertNull(parser.parse(subject, "Beneficios: Gift Card de S/ 500", LocalDateTime.now()));
    }

    @Test
    void shouldPreserveInternalTransfers() {
        ParsedEmailTransaction tx = parser.parse("Constancia de Transferencia Entre mis Cuentas BCP",
                "Realizaste una transferencia entre mis cuentas. Monto: S/ 500.00",
                LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(FlowType.INTERNAL_TRANSFER, tx.getFlowType());
    }

    @Test
    void shouldPreserveIncomingYapeThroughBcp() {
        ParsedEmailTransaction tx = parser.parse("Recepción de yapeo BCP",
                "Recibiste un yapeo de S/ 45.00 de Maria Perez.", LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(FlowType.INCOME, tx.getFlowType());
        assertEquals(new BigDecimal("45.00"), tx.getAmount());
        assertEquals("YAPE", tx.getChannel());
    }

    @Test
    void shouldRejectConflictingFlowEvidence() {
        assertNull(parser.parse("Constancia de pago BCP",
                "Recibiste un yapeo de S/ 45.00 Monto: S/ 45.00", LocalDateTime.now()));
    }

    @Test
    void shouldNotAssumeAnUnspecifiedTransferDirectionIsAnExpense() {
        assertNull(parser.parse("Constancia de transferencia BCP", "Monto: S/ 500.00",
                LocalDateTime.now()));
    }

    @Test
    void shouldRecognizeAnIncomingBankTransfer() {
        ParsedEmailTransaction tx = parser.parse("Constancia de transferencia BCP",
                "Recibiste una transferencia por S/ 500.00. Monto: S/ 500.00", LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(FlowType.INCOME, tx.getFlowType());
        assertEquals(new BigDecimal("500.00"), tx.getAmount());
    }

    @Test
    void shouldTreatRepeatedEquivalentAmountsAsOneOperation() {
        ParsedEmailTransaction tx = parser.parse("Constancia de transferencia BCP",
                "Realizaste una transferencia por S/ 25.00 Monto: S/ 25.0 Importe: S/ 25",
                LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(new BigDecimal("25.00"), tx.getAmount());
    }

    @Test
    void shouldParseCreditCardExpenseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Crédito";
        String htmlBody = """
                <html>
                <body>
                    <p>Estimado cliente,</p>
                    <p>Le informamos que se ha realizado un consumo con su tarjeta terminada en 9999.</p>
                    <table>
                        <tr><td>Importe:</td><td>S/ 25.50</td></tr>
                        <tr><td>Establecimiento:</td><td>TIENDA EJEMPLO</td></tr>
                        <tr><td>Nro. de operación:</td><td>98765432</td></tr>
                    </table>
                </body>
                </html>
                """;

        ParsedEmailTransaction tx = parser.parse(subject, htmlBody, LocalDateTime.now());

        assertNotNull(tx);
        assertEquals(new BigDecimal("25.50"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("TIENDA EJEMPLO", tx.getMerchantName());
        assertEquals("TARJETA_CREDITO_BCP", tx.getChannel());
        assertEquals("9999", tx.getCardLast4());
        assertEquals("98765432", tx.getOperationNumber());
        assertEquals("98765432", tx.getReceiptReference());
        assertNotNull(tx.getTransactionHash());
    }

    @Test
    void shouldParseDebitCardUsdExpenseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Débito";
        String htmlBody = """
                <div>
                    <h3>Detalle de operación</h3>
                    <p>Tarjeta débito **** 8888</p>
                    <p>Monto: US$ 14.99</p>
                    <p>Comercio: STREAMING SERVICE</p>
                    <p>Número de operación: 12345678</p>
                </div>
                """;

        ParsedEmailTransaction tx = parser.parse(subject, htmlBody, LocalDateTime.now());

        assertNotNull(tx);
        assertEquals(new BigDecimal("14.99"), tx.getAmount());
        assertEquals("USD", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("STREAMING SERVICE", tx.getMerchantName());
        assertEquals("TARJETA_DEBITO_BCP", tx.getChannel());
        assertEquals("8888", tx.getCardLast4());
        assertEquals("12345678", tx.getOperationNumber());
    }

    @Test
    void shouldParseSpecialCharacterMerchantPurchaseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Débito BCP";
        String body = """
                Estimado cliente, Realizaste un consumo de S/ 19.90 con tu Tarjeta de Débito BCP en ONLINE*MARKET.COM LTD.
                Por tu seguridad, te enviamos los datos de tu operación.
                Monto Total del consumo S/ 19.90
                Datos de la operación
                Operación realizada Consumo Tarjeta de Débito
                Fecha y hora 05 de setiembre de 2026 - 10:00 AM
                Número de Tarjeta de Débito ************1234
                Empresa ONLINE*MARKET.COM LTD
                Número de operación 123456
                """;

        ParsedEmailTransaction tx = parser.parse(subject, body, LocalDateTime.of(2026, 9, 5, 10, 0));

        assertNotNull(tx);
        assertEquals(new BigDecimal("19.90"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("ONLINE*MARKET.COM LTD", tx.getMerchantName());
        assertEquals("TARJETA_DEBITO_BCP", tx.getChannel());
        assertEquals("1234", tx.getCardLast4());
        assertEquals("123456", tx.getOperationNumber());
    }

    @Test
    void shouldExcludeOwnCreditCardPaymentsWithoutChangingTheirLegacyIdentity() throws Exception {
        String subject = "Constancia de Pago de Tarjeta de Crédito Propia - Servicio de Notificaciones BCP";
        String body = """
                Hola cliente de prueba,
                Realizaste un pago a tu tarjeta de S/ 81.25 desde tu Cuenta yape.
                A continuación, te enviamos los datos de tu operación.
                Montos
                Monto pagado S/ 81.25
                Datos de la operación
                Operación realizada Pago de tarjeta propia BCP
                Fecha y hora 26 de Septiembre de 2026 - 08:19 AM
                Pagado a VISA Prueba **** 1111
                """;

        ParsedEmailTransaction tx = parser.parse(subject, body, LocalDateTime.of(2026, 9, 26, 8, 19));

        assertNotNull(tx);
        assertEquals(new BigDecimal("81.25"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.INTERNAL_TRANSFER, tx.getFlowType());
        assertEquals("Pago Tarjeta Crédito BCP", tx.getMerchantName());
        assertEquals(com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT, tx.getOperationType());
        assertEquals("1111", tx.getCardLast4());
        String legacyInput = "EXPENSE_81.25_pago tarjeta crédito bcp_202609260819_20260926";
        String legacyHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(legacyInput.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(legacyHash, tx.getTransactionHash());
        assertEquals(tx.getTransactionHash(), parser.parse(subject, body,
                LocalDateTime.of(2026, 9, 26, 8, 19)).getTransactionHash());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Pago de tarjeta propia", "Pago de tarjeta de crédito propia",
            "Pago de tarjeta de credito propia", "Pago a tu tarjeta" })
    void shouldRecognizeExplicitOwnCardPaymentVariants(String operation) {
        var tx = parser.parse("Constancia de " + operation, "Monto pagado: S/ 81.25", LocalDateTime.now());
        assertNotNull(tx);
        assertEquals(FlowType.INTERNAL_TRANSFER, tx.getFlowType());
        assertEquals("Pago Tarjeta Crédito BCP", tx.getMerchantName());
    }

    @Test
    void shouldNotExcludeThirdPartyCardPaymentsOrInventUnconfirmedPayments() {
        var thirdParty = parser.parse("Constancia de pago de tarjeta de crédito de terceros",
                "Monto pagado: S/ 81.25 Beneficiario: Persona Prueba", LocalDateTime.now());
        assertNotNull(thirdParty);
        assertEquals(FlowType.EXPENSE, thirdParty.getFlowType());
        assertEquals(com.store.api.model.enums.OperationType.UNKNOWN, thirdParty.getOperationType());
        var purchaseWithReminder = parser.parse("Aviso de operación: Consumo con Tarjeta de Crédito",
                "Realizaste un consumo de S/ 81.25. Recuerda realizar el pago a tu tarjeta a tiempo.", LocalDateTime.now());
        assertNotNull(purchaseWithReminder);
        assertEquals(FlowType.EXPENSE, purchaseWithReminder.getFlowType());
        assertNull(parser.parse("Recordatorio de pago a tu tarjeta", "Monto: S/ 81.25", LocalDateTime.now()));
        assertNull(parser.parse("Constancia de pago de tarjeta propia",
                "Recibiste una transferencia por S/ 81.25", LocalDateTime.now()));
    }
}
