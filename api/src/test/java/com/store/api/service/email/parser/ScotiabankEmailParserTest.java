package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScotiabankEmailParserTest {

    private ScotiabankEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScotiabankEmailParser();
    }

    @Test
    void shouldRestrictGmailSearchTermsToOfficialSenders() {
        List<String> terms = parser.gmailSearchTerms();
        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().allMatch(term -> term.startsWith("from:")));
        assertTrue(terms.contains("from:scotiabank.com.pe"));
    }

    @Test
    void shouldAcceptOfficialDomainsAndRejectSpoofedSenders() {
        assertTrue(parser.supports("scotiaenlinea@scotiabank.com.pe", "Constancia de operación"));
        assertTrue(parser.supports("Scotiabank Perú <avisos@avisos.scotiabank.com.pe>", "Consumo con tarjeta"));
        assertTrue(parser.supports("alertas@scotiabank.pe", "Constancia de transferencia"));

        assertFalse(parser.supports("Scotiabank <fake@scotiabank-actualiza.example>", "Constancia de pago"));
        assertFalse(parser.supports("notificaciones@gmail.com", "Constancia Scotiabank"));
    }

    @Test
    void shouldParseCreditCardExpenseWithLast4AndOperationNumber() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 14, 20);
        String html = """
                <html><body>
                  <p>Estimado cliente, realizaste un consumo con tu tarjeta de crédito terminada en 3456.</p>
                  <p>Establecimiento: RESTAURANTE DEMO Monto: S/ 92.00 Nro. de operación: SCOT-445511</p>
                </body></html>
                """;

        ParsedEmailTransaction tx = parser.parse("Constancia de consumo con tu tarjeta de crédito", html, date);
        assertNotNull(tx);
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals(OperationType.UNKNOWN, tx.getOperationType());
        assertEquals("TARJETA_CREDITO_SCOTIABANK", tx.getChannel());
        assertEquals("3456", tx.getCardLast4());
        assertEquals(new BigDecimal("92.00"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals("RESTAURANTE DEMO", tx.getMerchantName());
        assertEquals("SCOT-445511", tx.getOperationNumber());
    }

    @Test
    void shouldParseOutgoingAndIncomingPlinOperations() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 20, 10);

        ParsedEmailTransaction outgoing = parser.parse(
                "Constancia de envío Plin",
                "Realizaste un envío por Plin. Destinatario: Sofía Castro Monto enviado: S/ 18.00 Código de operación: 332211",
                date);
        assertNotNull(outgoing);
        assertEquals(FlowType.EXPENSE, outgoing.getFlowType());
        assertEquals("PLIN", outgoing.getChannel());
        assertEquals("Sofía Castro", outgoing.getMerchantName());
        assertEquals(new BigDecimal("18.00"), outgoing.getAmount());

        ParsedEmailTransaction incoming = parser.parse(
                "Constancia de recepción Plin",
                "Recibiste un Plin de Diego León por S/ 55.00. Código de operación: 665544",
                date);
        assertNotNull(incoming);
        assertEquals(FlowType.INCOME, incoming.getFlowType());
        assertEquals("PLIN", incoming.getChannel());
        assertEquals("Diego León", incoming.getMerchantName());
        assertEquals(new BigDecimal("55.00"), incoming.getAmount());
    }

    @Test
    void shouldClassifyInternalTransfersAndOwnCardPaymentsAsInternalTransfer() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 9, 30);

        ParsedEmailTransaction ownTransfer = parser.parse(
                "Constancia de transferencia entre mis cuentas",
                "Realizaste una transferencia entre tus cuentas por S/ 410.00 Nro. operación: 771122",
                date);
        assertNotNull(ownTransfer);
        assertEquals(FlowType.INTERNAL_TRANSFER, ownTransfer.getFlowType());
        assertEquals("SCOTIABANK_TRANSFERENCIA", ownTransfer.getChannel());

        ParsedEmailTransaction cardRepayment = parser.parse(
                "Constancia de pago de tarjeta de crédito propia",
                "Realizaste un pago de tu tarjeta de crédito propia terminada en 3456. Monto pagado: S/ 700.00 Nro. operación: 882233",
                date);
        assertNotNull(cardRepayment);
        assertEquals(FlowType.INTERNAL_TRANSFER, cardRepayment.getFlowType());
        assertEquals(OperationType.OWN_CARD_PAYMENT, cardRepayment.getOperationType());
        assertEquals("TARJETA_CREDITO_SCOTIABANK", cardRepayment.getChannel());
        assertEquals("3456", cardRepayment.getCardLast4());
    }

    @Test
    void shouldIgnoreNonFinancialAndAmbiguousEmails() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);

        assertNull(parser.parse("Tu clave Token Scotiabank",
                "Tu código de verificación es 998811. Monto: S/ 100.00", date));
        assertNull(parser.parse("Estado de cuenta Scotiabank",
                "Tu estado de cuenta de noviembre. Monto total: S/ 1,400.00", date));
        assertNull(parser.parse("Constancia de pago",
                "Monto: S/ 25.00 Importe: S/ 50.00", date));
        assertNull(parser.parse("Constancia de operación",
                "Recibiste una transferencia de S/ 40.00 y realizaste un pago de S/ 40.00", date));
    }

    @Test
    void shouldProduceDistinctHashFromSameOperationInBbva() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);
        BbvaEmailParser bbva = new BbvaEmailParser();

        ParsedEmailTransaction scotiaTx = parser.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);
        ParsedEmailTransaction bbvaTx = bbva.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);

        assertNotNull(scotiaTx);
        assertNotNull(bbvaTx);
        assertNotEquals(bbvaTx.getTransactionHash(), scotiaTx.getTransactionHash());
    }
}
