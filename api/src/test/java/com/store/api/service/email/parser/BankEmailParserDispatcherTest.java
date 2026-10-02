package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BankEmailParserDispatcherTest {

    private BankEmailParserDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        YapeEmailParser yapeParser = new YapeEmailParser();
        BcpEmailParser bcpParser = new BcpEmailParser();
        dispatcher = new BankEmailParserDispatcher(List.of(yapeParser, bcpParser));
    }

    @Test
    void shouldRejectTheSurveyEvenWhenSentFromAnAllowedDomain() {
        String body = "Te invitamos a participar de una encuesta. "
                + "Participa por Gift Cards por un valor de S/ 500.";
        for (String sender : List.of("survey@activasite.com", "notificaciones@notificacionesbcp.com.pe")) {
            assertTrue(dispatcher.dispatchAndParse(sender, "Ayúdanos a saber lo que necesitas",
                    body, LocalDateTime.now()).isEmpty());
        }
    }

    @Test
    void shouldRejectAnUntrustedSenderRegardlessOfTheSubject() {
        String body = "Monto total S/ 500.00 Destino: TIENDA EJEMPLO ID de operación: 123ABC";
        for (String sender : List.of("BCP Yape <marketing@example.test>",
                "notificaciones@yape.pe.example.test", "notificaciones@notificacionesbcp.com.pe.example.test")) {
            assertTrue(dispatcher.dispatchAndParse(sender, "¡Tu pago en TIENDA BCP fue exitoso!",
                    body, LocalDateTime.now()).isEmpty());
        }
        assertTrue(dispatcher.dispatchAndParse(null, "Constancia de transferencia BCP",
                "Monto: S/ 500", LocalDateTime.now()).isEmpty());
    }

    @Test
    void shouldNotAcceptPromotionsThroughAnotherParser() {
        for (String sender : List.of("notificaciones@yape.pe", "notificaciones@notificacionesbcp.com.pe")) {
            assertTrue(dispatcher.dispatchAndParse(sender, "Yape BCP: premios para ti",
                    "Participa por S/ 500.00", LocalDateTime.now()).isEmpty());
        }
    }

    @Test
    void shouldDispatchIncomingYapeFromBcpThroughTheBcpParser() {
        Optional<ParsedEmailTransaction> result = dispatcher.dispatchAndParse(
                "BCP <notificaciones@notificacionesbcp.com.pe>", "Recepción de yapeo BCP",
                "Recibiste un yapeo de S/ 45.00 de Maria Perez.", LocalDateTime.now());
        assertTrue(result.isPresent());
        assertEquals(com.store.api.model.enums.FlowType.INCOME, result.get().getFlowType());
        assertEquals(new BigDecimal("45.00"), result.get().getAmount());
    }

    @Test
    void shouldDispatchBcpEmailCorrectly() {
        String sender = "BCP Notificaciones <notificaciones@notificacionesbcp.com.pe>";
        String subject = "Realizaste un consumo con tu Tarjeta de Crédito BCP";
        String body = "Monto: S/ 10.00 Establecimiento: TIENDA EJEMPLO Nro. de operación: 0000123456";

        Optional<ParsedEmailTransaction> result = dispatcher.dispatchAndParse(sender, subject, body, LocalDateTime.now());

        assertTrue(result.isPresent());
        ParsedEmailTransaction tx = result.get();
        assertEquals(new BigDecimal("10.00"), tx.getAmount());
        assertEquals("TIENDA EJEMPLO", tx.getMerchantName());
        assertEquals("TARJETA_CREDITO_BCP", tx.getChannel());
        assertEquals("0000123456", tx.getOperationNumber());
    }

    @Test
    void shouldDispatchYapeEmailCorrectly() {
        String sender = "YAPE Notificaciones <notificaciones@yape.pe>";
        String subject = "¡Tu pago en COMERCIO EJEMPLO fue exitoso!";
        String body = "Monto total S/ 25.00 Destino: COMERCIO EJEMPLO ID de operación: 01ABCDEF998877665544332211";

        Optional<ParsedEmailTransaction> result = dispatcher.dispatchAndParse(sender, subject, body, LocalDateTime.now());

        assertTrue(result.isPresent());
        ParsedEmailTransaction tx = result.get();
        assertEquals(new BigDecimal("25.00"), tx.getAmount());
        assertEquals("COMERCIO EJEMPLO", tx.getMerchantName());
        assertEquals("YAPE", tx.getChannel());
        assertEquals("01ABCDEF998877665544332211", tx.getOperationNumber());
    }

    @Test
    void shouldIgnoreNonBankEmailsGracefully() {
        String sender = "Newsletter <marketing@tienda.com>";
        String subject = "¡Grandes descuentos de temporada!";
        String body = "Aprovecha nuestras ofertas exclusivas en ropa y calzado.";

        Optional<ParsedEmailTransaction> result = dispatcher.dispatchAndParse(sender, subject, body, LocalDateTime.now());

        assertFalse(result.isPresent());
    }
}
