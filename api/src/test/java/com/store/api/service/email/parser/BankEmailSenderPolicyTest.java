package com.store.api.service.email.parser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankEmailSenderPolicyTest {

    private static final Set<String> ALLOWED_DOMAINS = Set.of("notificacionesbcp.com.pe");

    @ParameterizedTest
    @ValueSource(strings = {
            "notificaciones@notificacionesbcp.com.pe",
            "BCP Notificaciones <notificaciones@notificacionesbcp.com.pe>",
            "BCP <NOTIFICACIONES@NOTIFICACIONESBCP.COM.PE>"
    })
    void acceptsAnExactMailboxDomain(String sender) {
        assertTrue(BankEmailSenderPolicy.matchesDomain(sender, ALLOWED_DOMAINS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "BCP", "notificacionesbcp.com.pe <marketing@example.test>",
            "notificaciones@notificacionesbcp.com.pe.example.test",
            "notificaciones@fake-notificacionesbcp.com.pe",
            "notificaciones@notificacionesbcp.com.pe, marketing@example.test",
            "BCP: notificaciones@notificacionesbcp.com.pe;"
    })
    void rejectsMissingMalformedOrMisleadingSenders(String sender) {
        assertFalse(BankEmailSenderPolicy.matchesDomain(sender, ALLOWED_DOMAINS));
    }
}
