package com.store.api.controller.transaction;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.dto.transaction.TransactionOriginResponse;
import com.store.api.model.enums.CaptureOrigin;
import com.store.api.service.transaction.TransactionOriginService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionOriginControllerTest {
    private final TransactionOriginService service = mock(TransactionOriginService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionOriginController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void returnsMinimalUncacheableEvidence() throws Exception {
        when(service.getOrigin(7L)).thenReturn(new TransactionOriginResponse(CaptureOrigin.EMAIL,LocalDateTime.of(2026,1,3,12,0),"OP-123"));
        mvc.perform(get("/api/v1/transactions/7/origin")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.origin").value("EMAIL")).andExpect(jsonPath("$.operationReference").value("OP-123"))
                .andExpect(jsonPath("$.registeredAt").value("2026-01-03T12:00:00"))
                .andExpect(jsonPath("$.rawText").doesNotHaveJsonPath()).andExpect(jsonPath("$.rawNotificationText").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user").doesNotHaveJsonPath()).andExpect(jsonPath("$.email").doesNotHaveJsonPath());
    }

    @Test void missingOrForeignMovementsAreIndistinguishable() throws Exception {
        when(service.getOrigin(8L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Movimiento no encontrado."));
        mvc.perform(get("/api/v1/transactions/8/origin")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Movimiento no encontrado."));
    }
}
