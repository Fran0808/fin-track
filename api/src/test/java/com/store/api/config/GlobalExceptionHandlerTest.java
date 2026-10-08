package com.store.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test void unexpectedErrorsDoNotExposeSqlOrFinancialDetails() {
        var response = new GlobalExceptionHandler().handleGeneralException(
                new DataIntegrityViolationException("SQL update transactions; private financial details"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("No se pudieron guardar los cambios. Inténtalo de nuevo.", response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains("private financial details"));
        assertFalse(response.getBody().toString().contains("SQL"));
    }
}
