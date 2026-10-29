package com.store.api.controller.transaction;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.dto.transaction.*;
import com.store.api.model.enums.CaptureOrigin;
import com.store.api.service.transaction.ManualTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManualTransactionControllerTest {
    private final ManualTransactionService service = mock(ManualTransactionService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new ManualTransactionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final String valid = """
            {"requestId":"01234567-89ab-cdef-0123-456789abcdef","amount":"12.34","flowType":"EXPENSE",
             "contactName":"Synthetic store","paymentMethod":"CASH","transactionDate":"2026-01-31T12:00:00"}
            """;

    @Test void acceptsDecimalTextAndDoesNotExposePrivateIdempotencyFingerprint() throws Exception {
        when(service.create(any())).thenReturn(TransactionResponse.builder().id(7L).captureOrigin(CaptureOrigin.MANUAL).build());
        mvc.perform(post("/api/v1/transactions/manual").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.captureOrigin").value("MANUAL"))
                .andExpect(jsonPath("$.manualRequestFingerprint").doesNotHaveJsonPath());
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","12.345","100000000","null"})
    void rejectsInvalidAmountsBeforePersistence(String amount) throws Exception {
        mvc.perform(post("/api/v1/transactions/manual").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("\"12.34\"",amount))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void rejectsMissingOrMalformedIdentityAndUnreviewedReconciliation() throws Exception {
        mvc.perform(post("/api/v1/transactions/manual").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("01234567-89ab-cdef-0123-456789abcdef","bad"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transactions/manual/1/reconcile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"capturedId\":2}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
